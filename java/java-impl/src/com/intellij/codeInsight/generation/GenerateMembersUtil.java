// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.impl.light.LightTypeElement;
import com.intellij.psi.impl.source.codeStyle.JavaCodeStyleManagerImpl;
import com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.generate.GenerationUtil;
import org.jetbrains.java.generate.exception.GenerateCodeException;
import org.jetbrains.java.generate.template.TemplatesManager;

import java.util.*;
import java.util.stream.Collectors;

public final class GenerateMembersUtil {
  private static final Logger LOG = Logger.getInstance(GenerateMembersUtil.class);

  private GenerateMembersUtil() {
  }

  @NotNull
  public static <T extends GenerationInfo> List<T> insertMembersAtOffset(@NotNull PsiFile file,
                                                                         int offset,
                                                                         @NotNull List<T> memberPrototypes) throws IncorrectOperationException {
    return insertMembersAtOffset(file, offset, memberPrototypes, leaf -> findClassAtOffset(file, leaf));
  }

  @NotNull
  public static <T extends GenerationInfo> List<T> insertMembersAtOffset(@NotNull PsiClass psiClass,
                                                                         int offset,
                                                                         @NotNull List<T> memberPrototypes) throws IncorrectOperationException {
    return insertMembersAtOffset(psiClass.getContainingFile(), offset, memberPrototypes, leaf -> psiClass);
  }

  @NotNull
  private static <T extends GenerationInfo> List<T> insertMembersAtOffset(@NotNull PsiFile file,
                                                                          int offset,
                                                                          @NotNull List<T> memberPrototypes,
                                                                          @NotNull Function<? super PsiElement, ? extends PsiClass> aClassFunction) throws IncorrectOperationException {
    if (memberPrototypes.isEmpty()) return memberPrototypes;
    final PsiElement leaf = file.findElementAt(offset);
    if (leaf == null) return Collections.emptyList();

    PsiClass aClass = aClassFunction.fun(leaf);
    if (aClass == null) return Collections.emptyList();
    PsiElement anchor = memberPrototypes.get(0).findInsertionAnchor(aClass, leaf);

    if (anchor instanceof PsiWhiteSpace) {
      final ASTNode spaceNode = anchor.getNode();
      anchor = anchor.getNextSibling();

      assert spaceNode != null;
      if (spaceNode.getStartOffset() <= offset && spaceNode.getStartOffset() + spaceNode.getTextLength() >= offset) {
        String whiteSpace = spaceNode.getText().substring(0, offset - spaceNode.getStartOffset());
        if (!StringUtil.containsLineBreak(whiteSpace)) {
          // There is a possible case that the caret is located at the end of the line that already contains expression, say, we
          // want to override particular method while caret is located after the field.
          // Example - consider that we want to override toString() method at the class below:
          //     class Test {
          //         int i;<caret>
          //     }
          // We want to add line feed then in order to avoid situation like below:
          //     class Test {
          //         int i;@Override String toString() {
          //             super.toString();
          //         }
          //     }
          whiteSpace += "\n";
        }
        final PsiParserFacade parserFacade = PsiParserFacade.getInstance(file.getProject());
        final ASTNode singleNewLineWhitespace = parserFacade.createWhiteSpaceFromText(whiteSpace).getNode();
        if (singleNewLineWhitespace != null) {
          spaceNode.getTreeParent().replaceChild(spaceNode, singleNewLineWhitespace); // See http://jetbrains.net/jira/browse/IDEADEV-12837
        }
      }
    }

    // Q: shouldn't it be somewhere in PSI?
    PsiElement element = anchor;
    while (true) {
      if (element == null) break;
      if (element instanceof PsiField || element instanceof PsiMethod || element instanceof PsiClassInitializer) break;
      element = element.getNextSibling();
    }
    if (element instanceof PsiField field) {
      PsiTypeElement typeElement = field.getTypeElement();
      if (typeElement != null && !field.equals(typeElement.getParent())) {
        field.normalizeDeclaration();
        anchor = field;
      }
    }

    return insertMembersBeforeAnchor(aClass, anchor, memberPrototypes);
  }

  @NotNull
  public static <T extends GenerationInfo> List<T> insertMembersBeforeAnchor(@NotNull PsiClass aClass, @Nullable PsiElement anchor, @NotNull List<T> memberPrototypes) throws IncorrectOperationException {
    boolean before = true;
    for (T memberPrototype : memberPrototypes) {
      memberPrototype.insert(aClass, anchor, before);
      before = false;
      anchor = memberPrototype.getPsiMember();
    }
    return memberPrototypes;
  }

  /**
   * @see GenerationInfo#positionCaret(Editor, boolean)
   */
  public static void positionCaret(@NotNull Editor editor, @NotNull PsiElement firstMember, boolean toEditMethodBody) {
    LOG.assertTrue(firstMember.isValid());
    Project project = firstMember.getProject();

    if (toEditMethodBody) {
      PsiMethod method = (PsiMethod)firstMember;
      PsiCodeBlock body = method.getBody();
      if (body != null) {
        PsiElement firstBodyElement = body.getFirstBodyElement();
        PsiElement l = firstBodyElement;
        while (l instanceof PsiWhiteSpace) l = l.getNextSibling();
        if (l == null) l = body;
        PsiElement lastBodyElement = body.getLastBodyElement();
        PsiElement r = lastBodyElement;
        while (r instanceof PsiWhiteSpace) r = r.getPrevSibling();
        if (r == null) r = body;

        int start = l.getTextRange().getStartOffset();
        int end = r.getTextRange().getEndOffset();

        boolean adjustLineIndent = false;

        // body is whitespace
        if (start > end &&
            firstBodyElement == lastBodyElement &&
            firstBodyElement instanceof PsiWhiteSpaceImpl
          ) {
          CharSequence chars = ((PsiWhiteSpaceImpl)firstBodyElement).getChars();
          if (chars.length() > 1 && chars.charAt(0) == '\n' && chars.charAt(1) == '\n') {
            start = end = firstBodyElement.getTextRange().getStartOffset() + 1;
            adjustLineIndent = true;
          }
        }

        editor.getCaretModel().moveToOffset(Math.min(start, end));
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        if (start < end) {
          //Not an empty body
          editor.getSelectionModel().setSelection(start, end);
        } else if (adjustLineIndent) {
          Document document = editor.getDocument();
          RangeMarker marker = document.createRangeMarker(start, start);
          PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
          if (marker.isValid()) {
            CodeStyleManager.getInstance(project).adjustLineIndent(document, marker.getStartOffset());
          }
        }
        return;
      }
    }

    int offset;
    if (firstMember instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)firstMember;
      PsiCodeBlock body = method.getBody();
      if (body == null) {
        offset = method.getTextRange().getStartOffset();
      }
      else {
        PsiJavaToken lBrace = body.getLBrace();
        assert lBrace != null : firstMember.getText();
        offset = lBrace.getTextRange().getEndOffset();
      }
    }
    else {
      offset = firstMember.getTextRange().getStartOffset();
    }

    editor.getCaretModel().moveToOffset(offset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().removeSelection();
  }

  public static PsiElement insert(@NotNull PsiClass aClass, @NotNull PsiMember member, @Nullable PsiElement anchor, boolean before) throws IncorrectOperationException {
    if (member instanceof PsiMethod) {
      if (!aClass.isInterface()) {
        final PsiParameter[] parameters = ((PsiMethod)member).getParameterList().getParameters();
        final boolean generateFinals = JavaCodeStyleSettings.getInstance(aClass.getContainingFile()).GENERATE_FINAL_PARAMETERS;
        for (final PsiParameter parameter : parameters) {
          PsiUtil.setModifierProperty(parameter, PsiModifier.FINAL, generateFinals);
        }
      }
    }

    if (anchor != null) {
      return before ? aClass.addBefore(member, anchor) : aClass.addAfter(member, anchor);
    }
    else {
      return aClass.add(member);
    }
  }

  @Nullable
  private static PsiClass findClassAtOffset(@NotNull PsiFile file, PsiElement leaf) {
    PsiElement element = leaf;
    while (element != null && !(element instanceof PsiFile)) {
      if (element instanceof PsiClass psiClass && !(element instanceof PsiTypeParameter)) {
        if (psiClass.isEnum()) {
          PsiElement lastChild = null;
          for (PsiElement child = psiClass.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof PsiJavaToken && ";".equals(child.getText())) {
              lastChild = child;
              break;
            }
            else if (child instanceof PsiJavaToken && ",".equals(child.getText()) || child instanceof PsiEnumConstant) {
              lastChild = child;
            }
          }
          if (lastChild != null) {
            int adjustedOffset = lastChild.getTextRange().getEndOffset();
            if (leaf.getTextRange().getEndOffset() <= adjustedOffset) return findClassAtOffset(file, file.findElementAt(adjustedOffset));
          }
        }
        return psiClass;
      }
      element = element.getParent();
    }
    return null;
  }

  @NotNull
  public static PsiMethod substituteGenericMethod(@NotNull PsiMethod method, @NotNull PsiSubstitutor substitutor) {
    return substituteGenericMethod(method, substitutor, null);
  }

  @NotNull
  public static PsiMethod substituteGenericMethod(@NotNull PsiMethod sourceMethod,
                                                  @NotNull PsiSubstitutor substitutor,
                                                  @Nullable PsiElement target) {
    final Project project = sourceMethod.getProject();
    final JVMElementFactory factory = getFactory(sourceMethod.getProject(), target);
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);

    try {
      PsiMethod resultMethod = createMethod(factory, sourceMethod, target);
      copyModifiers(sourceMethod.getModifierList(), resultMethod.getModifierList());
      PsiSubstitutor collisionResolvedSubstitutor =
        substituteTypeParameters(factory, target, sourceMethod.getTypeParameterList(), resultMethod.getTypeParameterList(), substitutor, sourceMethod);
      substituteReturnType(PsiManager.getInstance(project), resultMethod, sourceMethod.getReturnType(), collisionResolvedSubstitutor);
      PsiTypeElement typeElement = resultMethod.getReturnTypeElement();
      if (typeElement != null && typeElement.getText().startsWith("@")) {
        // If return type is annotated, substituteReturnType will add the annotation into type element,
        // so the method should be reparsed to move it to the modifier list
        resultMethod = factory.createMethodFromText(resultMethod.getText(), target);
      }
      substituteParameters(factory, codeStyleManager, sourceMethod.getParameterList(), resultMethod.getParameterList(), collisionResolvedSubstitutor, target);
      copyDocComment(sourceMethod, resultMethod, factory);
      GlobalSearchScope scope = sourceMethod.getResolveScope();
      List<PsiClassType> thrownTypes = ExceptionUtil.collectSubstituted(collisionResolvedSubstitutor, sourceMethod.getThrowsList().getReferencedTypes(), scope);
      if (target instanceof PsiClass) {
        for (PsiMethod psiMethod : ((PsiClass)target).findMethodsBySignature(sourceMethod, true)) {
          if (psiMethod != null && psiMethod != sourceMethod && !MethodSignatureUtil.isSuperMethod(psiMethod, sourceMethod)) {
            PsiClass aSuper = psiMethod.getContainingClass();
            if (aSuper != null && aSuper != target) {
              PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(aSuper, (PsiClass)target, PsiSubstitutor.EMPTY);
              ExceptionUtil.retainExceptions(thrownTypes, ExceptionUtil.collectSubstituted(superClassSubstitutor, psiMethod.getThrowsList().getReferencedTypes(), scope));
            }
          }
        }
      }
      substituteThrows(factory, resultMethod.getThrowsList(), collisionResolvedSubstitutor, sourceMethod, thrownTypes);
      return resultMethod;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return sourceMethod;
    }
  }

  private static void copyModifiers(@NotNull PsiModifierList sourceModifierList,
                                    @NotNull PsiModifierList targetModifierList) {
    VisibilityUtil.setVisibility(targetModifierList, VisibilityUtil.getVisibilityModifier(sourceModifierList));
  }

  @NotNull
  private static PsiSubstitutor substituteTypeParameters(@NotNull JVMElementFactory factory,
                                                         @Nullable PsiElement target,
                                                         @Nullable PsiTypeParameterList sourceTypeParameterList,
                                                         @Nullable PsiTypeParameterList targetTypeParameterList,
                                                         @NotNull PsiSubstitutor substitutor,
                                                         @NotNull PsiMethod sourceMethod) {
    if (sourceTypeParameterList == null || targetTypeParameterList == null || PsiUtil.isRawSubstitutor(sourceMethod, substitutor)) {
      return substitutor;
    }

    final Map<PsiTypeParameter, PsiType> substitutionMap = new HashMap<>(substitutor.getSubstitutionMap());
    for (PsiTypeParameter typeParam : sourceTypeParameterList.getTypeParameters()) {
      final PsiTypeParameter substitutedTypeParam = substituteTypeParameter(factory, typeParam, substitutor, sourceMethod);

      final PsiTypeParameter resolvedTypeParam = resolveTypeParametersCollision(factory, sourceTypeParameterList, target,
                                                                                substitutedTypeParam, substitutor);
      targetTypeParameterList.add(resolvedTypeParam);
      if (substitutedTypeParam != resolvedTypeParam) {
        substitutionMap.put(typeParam, factory.createType(resolvedTypeParam));
      }
    }
    return substitutionMap.isEmpty() ? substitutor : factory.createSubstitutor(substitutionMap);
  }

  @NotNull
  private static PsiTypeParameter resolveTypeParametersCollision(@NotNull JVMElementFactory factory,
                                                                 @NotNull PsiTypeParameterList sourceTypeParameterList,
                                                                 @Nullable PsiElement target,
                                                                 @NotNull PsiTypeParameter typeParam,
                                                                 @NotNull PsiSubstitutor substitutor) {
    String typeParamName = typeParam.getName();
    for (PsiType type : substitutor.getSubstitutionMap().values()) {
      if (type != null && Objects.equals(type.getCanonicalText(), typeParamName)) {
        final String newName = suggestUniqueTypeParameterName(typeParamName, sourceTypeParameterList, PsiTreeUtil.getParentOfType(target, PsiClass.class, false));
        final PsiTypeParameter newTypeParameter = factory.createTypeParameter(newName, typeParam.getSuperTypes());
        substitutor.put(typeParam, factory.createType(newTypeParameter));
        return newTypeParameter;
      }
    }
    return factory.createTypeParameter(typeParamName, typeParam.getSuperTypes());
  }

  @NotNull
  private static String suggestUniqueTypeParameterName(@NotNull String baseName, @NotNull PsiTypeParameterList typeParameterList, @Nullable PsiClass targetClass) {
    int i = 0;
    while (true) {
      final String newName = baseName + ++i;
      if (checkUniqueTypeParameterName(newName, typeParameterList) && (targetClass == null || checkUniqueTypeParameterName(newName, targetClass.getTypeParameterList()))) {
        return newName;
      }
    }
  }


  private static boolean checkUniqueTypeParameterName(@NotNull String baseName, @Nullable PsiTypeParameterList typeParameterList) {
    if (typeParameterList == null) return true;

    for (PsiTypeParameter typeParameter : typeParameterList.getTypeParameters()) {
      if (Objects.equals(typeParameter.getName(), baseName)) {
        return false;
      }
    }
    return true;
  }


  @NotNull
  private static PsiTypeParameter substituteTypeParameter(@NotNull JVMElementFactory factory,
                                                          @NotNull PsiTypeParameter typeParameter,
                                                          @NotNull PsiSubstitutor substitutor,
                                                          @NotNull PsiMethod sourceMethod) {
    if (typeParameter instanceof LightElement) {
      List<PsiClassType> substitutedSupers = ContainerUtil.map(typeParameter.getSuperTypes(), t -> ObjectUtils.notNull(toClassType(substitutor.substitute(t)), t));
      return factory.createTypeParameter(Objects.requireNonNull(typeParameter.getName()), substitutedSupers.toArray(PsiClassType.EMPTY_ARRAY));
    }
    final PsiElement copy = ObjectUtils.notNull(typeParameter instanceof PsiCompiledElement ? ((PsiCompiledElement)typeParameter).getMirror() : typeParameter, typeParameter).copy();
    LOG.assertTrue(copy != null, typeParameter);
    final Map<PsiElement, PsiElement> replacementMap = new HashMap<>();
    copy.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        final PsiElement resolve = reference.resolve();
        if (resolve instanceof PsiTypeParameter) {
          final PsiType type = factory.createType((PsiTypeParameter)resolve);
          replacementMap.put(reference, factory.createReferenceElementByType((PsiClassType)substituteType(substitutor, type, sourceMethod, null)));
        }
      }
    });
    return (PsiTypeParameter)CommonJavaRefactoringUtil.replaceElementsWithMap(copy, replacementMap);
  }

  private static PsiClassType toClassType(PsiType type) {
    if (type instanceof PsiClassType) {
      return (PsiClassType)type;
    }
    if (type instanceof PsiCapturedWildcardType) {
      return toClassType(((PsiCapturedWildcardType)type).getUpperBound());
    }
    if (type instanceof PsiWildcardType) {
      return toClassType(((PsiWildcardType)type).getBound());
    }
    return null;
  }

  private static void substituteParameters(@NotNull JVMElementFactory factory,
                                           @NotNull JavaCodeStyleManager codeStyleManager,
                                           @NotNull PsiParameterList sourceParameterList,
                                           @NotNull PsiParameterList targetParameterList,
                                           @NotNull PsiSubstitutor substitutor, PsiElement target) {
    final PsiParameter[] parameters = sourceParameterList.getParameters();
    final PsiParameter[] newParameters = overriddenParameters(parameters, factory, codeStyleManager, substitutor, target);
    for (int i = 0; i < newParameters.length; i++) {
      final PsiParameter newParameter = newParameters[i];
      copyOrReplaceModifierList(parameters[i], target, newParameter);
      targetParameterList.add(newParameter);
    }
  }

  public static PsiParameter @NotNull [] overriddenParameters(PsiParameter @NotNull [] parameters,
                                                              @NotNull JVMElementFactory factory,
                                                              @NotNull JavaCodeStyleManager codeStyleManager,
                                                              @NotNull PsiSubstitutor substitutor,
                                                              @Nullable PsiElement target) {
    PsiParameter[] result = new PsiParameter[parameters.length];
    UniqueNameGenerator generator = new UniqueNameGenerator();

    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      final PsiType parameterType = parameter.getType();
      PsiElement declarationScope = parameter.getDeclarationScope();
      PsiType substituted = declarationScope instanceof PsiTypeParameterListOwner ? substituteType(substitutor, parameterType, (PsiTypeParameterListOwner)declarationScope, parameter.getModifierList()) 
                                                                                  : parameterType;
      String paramName = parameter.getName();
      boolean isBaseNameGenerated = true;
      final boolean isSubstituted = substituted.equals(parameterType);
      if (!isSubstituted && isBaseNameGenerated(codeStyleManager, TypeConversionUtil.erasure(parameterType), paramName)) {
        isBaseNameGenerated = false;
      }

      if (isBaseNameGenerated && !isSubstituted && isBaseNameGenerated(codeStyleManager, parameterType, paramName) ||
          !factory.isValidParameterName(paramName)) {
        String[] names = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, substituted).names;
        if (names.length > 0) {
          paramName = generator.generateUniqueName(names[0]);
        }
        else {
          paramName = generator.generateUniqueName("p");
        }
      }
      else if (!generator.value(paramName)) {
        paramName = generator.generateUniqueName(paramName);
      }
      generator.addExistingName(paramName);
      PsiType expressionType = GenericsUtil.getVariableTypeByExpressionType(substituted);
      if (expressionType instanceof PsiArrayType && substituted instanceof PsiEllipsisType) {
        expressionType = new PsiEllipsisType(((PsiArrayType)expressionType).getComponentType())
          .annotate(expressionType.getAnnotationProvider());
      }
      result[i] = factory.createParameter(paramName, expressionType, target);
    }
    return result;
  }

  private static void substituteThrows(@NotNull JVMElementFactory factory,
                                       @NotNull PsiReferenceList targetThrowsList,
                                       @NotNull PsiSubstitutor substitutor,
                                       @NotNull PsiMethod sourceMethod,
                                       @NotNull List<? extends PsiClassType> thrownTypes) {
    for (PsiClassType thrownType : thrownTypes) {
      targetThrowsList.add(factory.createReferenceElementByType((PsiClassType)substituteType(substitutor, thrownType, sourceMethod, null)));
    }
  }

  private static void copyDocComment(@NotNull PsiMethod source, @NotNull PsiMethod target, @NotNull JVMElementFactory factory) {
    final PsiElement navigationElement = source.getNavigationElement();
    if (navigationElement instanceof PsiDocCommentOwner) {
      final PsiDocComment docComment = ((PsiDocCommentOwner)navigationElement).getDocComment();
      if (docComment != null) {
        target.addAfter(factory.createDocCommentFromText(docComment.getText()), null);
      }
    }
    final PsiParameter[] sourceParameters = source.getParameterList().getParameters();
    final PsiParameterList targetParameterList = target.getParameterList();
    CommonJavaRefactoringUtil.fixJavadocsForParams(target, Set.of(targetParameterList.getParameters()), pair -> {
      final int parameterIndex = targetParameterList.getParameterIndex(pair.first);
      if (parameterIndex >= 0 && parameterIndex < sourceParameters.length) {
        return Comparing.strEqual(pair.second, sourceParameters[parameterIndex].getName());
      }
      return false;
    });
  }

  @NotNull
  private static PsiMethod createMethod(@NotNull JVMElementFactory factory,
                                        @NotNull PsiMethod method, PsiElement target) {
    if (method.isConstructor()) {
      return factory.createConstructor(method.getName(), target);
    }
    return factory.createMethod(method.getName(), PsiTypes.voidType(), target);
  }

  private static void substituteReturnType(@NotNull PsiManager manager,
                                           @NotNull PsiMethod method,
                                           @Nullable PsiType returnType,
                                           @NotNull PsiSubstitutor substitutor) {
    final PsiTypeElement returnTypeElement = method.getReturnTypeElement();
    if (returnTypeElement == null || returnType == null) {
      return;
    }
    final PsiType substitutedReturnType = substituteType(substitutor, returnType, method, method.getModifierList());

    returnTypeElement.replace(new LightTypeElement(manager, substitutedReturnType instanceof PsiWildcardType ? TypeConversionUtil.erasure(substitutedReturnType) : substitutedReturnType));
  }

  @NotNull
  private static JVMElementFactory getFactory(@NotNull Project p, @Nullable PsiElement target) {
    return target == null ? JavaPsiFacade.getElementFactory(p) : JVMElementFactories.requireFactory(target.getLanguage(), p);
  }

  private static boolean isBaseNameGenerated(@NotNull JavaCodeStyleManager csManager, @NotNull PsiType parameterType, @NotNull String paramName) {
    if (Arrays.asList(csManager.suggestVariableName(VariableKind.PARAMETER, null, null, parameterType).names).contains(paramName)) {
      return true;
    }
    final String typeName = JavaCodeStyleManagerImpl.getTypeName(parameterType);
    return typeName != null &&
           NameUtil.getSuggestionsByName(typeName, "", "", false, false, parameterType instanceof PsiArrayType).contains(paramName);
  }

  private static PsiType substituteType(final PsiSubstitutor substitutor, final PsiType type, @NotNull PsiTypeParameterListOwner owner, PsiModifierList modifierList) {
    PsiType substitutedType = PsiUtil.isRawSubstitutor(owner, substitutor)
                    ? TypeConversionUtil.erasure(type)
                    : GenericsUtil.eliminateWildcards(substitutor.substitute(type), false, true);
    return substitutedType != null ? AnnotationTargetUtil.keepStrictlyTypeUseAnnotations(modifierList, substitutedType) : null;
  }

  public static boolean isChildInRange(PsiElement child, PsiElement first, PsiElement last) {
    if (child.equals(first)) return true;
    while (true) {
      if (child.equals(first)) return false; // before first
      if (child.equals(last)) return true;
      child = child.getNextSibling();
      if (child == null) return false;
    }
  }

  /**
   * May add @Override, body according to the override template and align throws list according to the super method
   */
  public static void setupGeneratedMethod(PsiMethod method) {
    PsiClass containingClass = method.getContainingClass();
    PsiClass base = containingClass == null ? null : containingClass.getSuperClass();
    PsiMethod overridden = base == null ? null : base.findMethodBySignature(method, true);

    boolean emptyTemplate = true;
    PsiCodeBlock body = method.getBody();
    if (body != null) {
      PsiJavaToken lBrace = body.getLBrace();
      int left = lBrace != null ? lBrace.getStartOffsetInParent() + 1 : 0;
      PsiJavaToken rBrace = body.getRBrace();
      int right = rBrace != null ? rBrace.getStartOffsetInParent() : body.getTextLength();
      emptyTemplate = StringUtil.isEmptyOrSpaces(body.getText().substring(left, right));
    }

    if (overridden == null) {
      if (emptyTemplate) {
        CreateFromUsageUtils.setupMethodBody(method, containingClass);
      }
      return;
    }

    PsiSubstitutor classSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(base, containingClass, PsiSubstitutor.EMPTY);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(method.getProject());
    String throwsList =
      Arrays.stream(overridden.getThrowsList().getReferencedTypes())
        .map(classSubstitutor::substitute)
        .filter(Objects::nonNull)
        .map(type -> type.getCanonicalText())
        .collect(Collectors.joining(", "));
    if (throwsList.isEmpty()) {
      method.getThrowsList().delete();
    }
    else {
      method.getThrowsList().replace(factory.createMethodFromText("void m() throws " + throwsList + ";", method).getThrowsList());
    }

    if (emptyTemplate) {
      OverrideImplementUtil.setupMethodBody(method, overridden, containingClass);
    }
    OverrideImplementUtil.annotateOnOverrideImplement(method, base, overridden);
  }

  public static void copyOrReplaceModifierList(@NotNull PsiModifierListOwner sourceParam, @Nullable PsiElement targetClass, @NotNull PsiModifierListOwner targetParam) {
    PsiModifierList sourceModifierList = sourceParam.getModifierList();
    PsiModifierList targetModifierList = targetParam.getModifierList();

    if (sourceModifierList != null && targetModifierList != null) {
      for (@PsiModifier.ModifierConstant String m : PsiModifier.MODIFIERS) {
        targetModifierList.setModifierProperty(m, sourceParam.hasModifierProperty(m));
      }

      OverrideImplementsAnnotationsHandler.repeatAnnotationsFromSource(sourceParam, targetClass, targetParam);
    }
  }

  public static void copyAnnotations(@NotNull PsiModifierList source, @NotNull PsiModifierList target, String... skipAnnotations) {
    for (PsiAnnotation annotation : source.getAnnotations()) {
      PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
      if (ref == null) continue;
      PsiClass oldClass = ObjectUtils.tryCast(ref.resolve(), PsiClass.class);
      if (oldClass == null) continue;
      String qualifiedName = oldClass.getQualifiedName();
      if (qualifiedName == null || ArrayUtil.contains(qualifiedName, skipAnnotations) || target.hasAnnotation(qualifiedName)) {
        continue;
      }
      PsiClass newClass = JavaPsiFacade.getInstance(target.getProject()).findClass(qualifiedName, target.getResolveScope());
      if (newClass == null || !oldClass.getManager().areElementsEquivalent(oldClass, newClass)) continue;
      AddAnnotationPsiFix.addPhysicalAnnotationIfAbsent(qualifiedName, annotation.getParameterList().getAttributes(), target);
    }
  }

  public static void copyAnnotations(@NotNull PsiModifierListOwner source, @NotNull PsiModifierListOwner target, String... skipAnnotations) {
    PsiModifierList targetModifierList = target.getModifierList();
    PsiModifierList sourceModifierList = source.getModifierList();
    if (targetModifierList == null || sourceModifierList == null) return;
    copyAnnotations(sourceModifierList, targetModifierList, skipAnnotations);
  }

  //java bean getters/setters
  public static PsiMethod generateSimpleGetterPrototype(@NotNull PsiField field) {
    return generatePrototype(field, PropertyUtilBase.generateGetterPrototype(field));
  }

  public static PsiMethod generateSimpleSetterPrototype(@NotNull PsiField field) {
    return generatePrototype(field, PropertyUtilBase.generateSetterPrototype(field));
  }

  public static PsiMethod generateSimpleSetterPrototype(PsiField field, PsiClass targetClass) {
    return generatePrototype(field, PropertyUtilBase.generateSetterPrototype(field, targetClass));
  }

  //custom getters/setters
  public static @NotNull String suggestGetterName(PsiField field) {
    return generateGetterPrototype(field).getName();
  }

  public static @NotNull String suggestGetterName(String name, PsiType type, Project project) {
    if (type instanceof PsiEllipsisType) {
      type = ((PsiEllipsisType)type).toArrayType();
    }
    return suggestGetterName(JavaPsiFacade.getElementFactory(project).createField(name, type));
  }

  public static @NotNull String suggestSetterName(PsiField field) {
    return generateSetterPrototype(field).getName();
  }

  public static @NotNull String suggestSetterName(String name, PsiType type, Project project) {
    if (type instanceof PsiEllipsisType) {
      type = ((PsiEllipsisType)type).toArrayType();
    }
    return suggestSetterName(JavaPsiFacade.getElementFactory(project).createField(name, type));
  }

  public static @NotNull PsiMethod generateGetterPrototype(@NotNull PsiField field) {
    return generateGetterPrototype(field, true);
  }

  public static @NotNull PsiMethod generateSetterPrototype(@NotNull PsiField field) {
    return generateSetterPrototype(field, true);
  }

  public static @NotNull PsiMethod generateSetterPrototype(@NotNull PsiField field, PsiClass aClass) {
    return generatePrototype(field, aClass, true, SetterTemplatesManager.getInstance());
  }

  static @NotNull PsiMethod generateGetterPrototype(@NotNull PsiField field, boolean ignoreInvalidTemplate) {
    return generatePrototype(field, field.getContainingClass(), ignoreInvalidTemplate, GetterTemplatesManager.getInstance());
  }

  static @NotNull PsiMethod generateSetterPrototype(@NotNull PsiField field, boolean ignoreInvalidTemplate) {
    return generatePrototype(field, field.getContainingClass(), ignoreInvalidTemplate, SetterTemplatesManager.getInstance());
  }

  private static @NotNull PsiMethod generatePrototype(@NotNull PsiField field,
                                                      PsiClass psiClass,
                                                      boolean ignoreInvalidTemplate,
                                                      @NotNull TemplatesManager templatesManager) {
    Project project = field.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    String template = templatesManager.getDefaultTemplate().getTemplate();
    Function<String, String> calculateTemplateText =
      currentTemplate ->
        GenerationUtil.velocityGenerateCode(psiClass, Collections.singletonList(field), new HashMap<>(), currentTemplate, 0, false);
    String methodText;
    try {
      methodText = calculateTemplateText.fun(template);
    }
    catch (GenerateCodeException e) {
      if (ignoreInvalidTemplate) {
        LOG.info(e);
        methodText = calculateTemplateText.fun(templatesManager.getDefaultTemplates().get(0).getTemplate());
      }
      else {
        throw e;
      }
    }

    boolean isGetter = templatesManager instanceof GetterTemplatesManager;
    PsiMethod result;
    try {
      result = factory.createMethodFromText(methodText, psiClass);
    }
    catch (IncorrectOperationException e) {
      if (ignoreInvalidTemplate) {
        LOG.info(e);
        result = isGetter ? PropertyUtilBase.generateGetterPrototype(field) : PropertyUtilBase.generateSetterPrototype(field);
      }
      else {
        throw new GenerateCodeException(e);
      }
    }
    result = (PsiMethod)CodeStyleManager.getInstance(project).reformat(result);

    PsiModifierListOwner annotationTarget;
    if (isGetter) {
      annotationTarget = result;
    }
    else {
      final PsiParameter[] parameters = result.getParameterList().getParameters();
      annotationTarget = parameters.length == 1 ? parameters[0] : null;
    }
    if (annotationTarget != null) {
      NullableNotNullManager.getInstance(project).copyNullableOrNotNullAnnotation(field, annotationTarget);
    }

    return generatePrototype(field, result);
  }

  @NotNull
  private static PsiMethod generatePrototype(@NotNull PsiField field, @NotNull PsiMethod result) {
    return setVisibility(field, annotateOnOverrideImplement(field.getContainingClass(), result));
  }

  @Contract("_, null -> null; _, !null -> !null")
  public static PsiMethod setVisibility(PsiMember member, PsiMethod prototype) {
    if (prototype == null) return null;

    PsiFile file = member.getContainingFile();
    JavaCodeStyleSettings javaSettings =
      file != null
      ? JavaCodeStyleSettings.getInstance(file)
      : CodeStyle.getProjectOrDefaultSettings(member.getProject()).getCustomSettings(JavaCodeStyleSettings.class);
    String visibility = javaSettings.VISIBILITY;

    @PsiModifier.ModifierConstant String newVisibility;
    final PsiClass containingClass = member.getContainingClass();
    if (VisibilityUtil.ESCALATE_VISIBILITY.equals(visibility)) {
      PsiClass aClass = member instanceof PsiClass ? (PsiClass)member : containingClass;
      newVisibility = PsiUtil.getSuitableModifierForMember(aClass, prototype.isConstructor());
    }
    else {
      newVisibility = (containingClass != null && containingClass.isRecord()) ? PsiModifier.PUBLIC : visibility;
    }
    VisibilityUtil.setVisibility(prototype.getModifierList(), newVisibility);

    return prototype;
  }

  @Contract("_, null -> null; _, !null -> !null")
  public static PsiMethod annotateOnOverrideImplement(@Nullable PsiClass targetClass, @Nullable PsiMethod generated) {
    if (generated == null || targetClass == null) return generated;

    if (JavaCodeStyleSettings.getInstance(targetClass.getContainingFile()).INSERT_OVERRIDE_ANNOTATION) {
      PsiMethod superMethod = targetClass.findMethodBySignature(generated, true);
      if (superMethod != null && superMethod.getContainingClass() != targetClass && PsiUtil.isAccessible(superMethod, targetClass, null)) {
        OverrideImplementUtil.annotateOnOverrideImplement(generated, targetClass, superMethod, true);
      }
      if (JavaPsiRecordUtil.getRecordComponentForAccessor(generated) != null) {
        AddAnnotationPsiFix
          .addPhysicalAnnotationIfAbsent(CommonClassNames.JAVA_LANG_OVERRIDE, PsiNameValuePair.EMPTY_ARRAY, generated.getModifierList());
      }
    }
    return generated;
  }
}