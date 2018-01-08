/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
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
import com.intellij.psi.impl.light.LightTypeElement;
import com.intellij.psi.impl.source.codeStyle.JavaCodeStyleManagerImpl;
import com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.generate.GenerationUtil;
import org.jetbrains.java.generate.exception.GenerateCodeException;
import org.jetbrains.java.generate.template.TemplatesManager;

import java.util.*;

public class GenerateMembersUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.GenerateMembersUtil");

  private GenerateMembersUtil() {
  }

  @NotNull
  public static <T extends GenerationInfo> List<T> insertMembersAtOffset(PsiFile file,
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
  private static <T extends GenerationInfo> List<T> insertMembersAtOffset(PsiFile file,
                                                                          int offset,
                                                                          @NotNull List<T> memberPrototypes,
                                                                          final Function<PsiElement, PsiClass> aClassFunction) throws IncorrectOperationException {
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
        final PsiParserFacade parserFacade = PsiParserFacade.SERVICE.getInstance(file.getProject());
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
    if (element instanceof PsiField) {
      PsiField field = (PsiField)element;
      PsiTypeElement typeElement = field.getTypeElement();
      if (typeElement != null && !field.equals(typeElement.getParent())) {
        field.normalizeDeclaration();
        anchor = field;
      }
    }

    return insertMembersBeforeAnchor(aClass, anchor, memberPrototypes);
  }

  @NotNull
  public static <T extends GenerationInfo> List<T> insertMembersBeforeAnchor(PsiClass aClass, @Nullable PsiElement anchor, @NotNull List<T> memberPrototypes) throws IncorrectOperationException {
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
        final boolean generateFinals = CodeStyleSettingsManager.getSettings(aClass.getProject())
          .getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_PARAMETERS;
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
  private static PsiClass findClassAtOffset(PsiFile file, PsiElement leaf) {
    PsiElement element = leaf;
    while (element != null && !(element instanceof PsiFile)) {
      if (element instanceof PsiClass && !(element instanceof PsiTypeParameter)) {
        final PsiClass psiClass = (PsiClass)element;
        if (psiClass.isEnum()) {
          PsiElement lastChild = null;
          for (PsiElement child : psiClass.getChildren()) {
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

  public static PsiMethod substituteGenericMethod(PsiMethod method, final PsiSubstitutor substitutor) {
    return substituteGenericMethod(method, substitutor, null);
  }

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
    for (PsiType type : substitutor.getSubstitutionMap().values()) {
      if (type != null && Comparing.equal(type.getCanonicalText(), typeParam.getName())) {
        final String newName = suggestUniqueTypeParameterName(typeParam.getName(), sourceTypeParameterList, PsiTreeUtil.getParentOfType(target, PsiClass.class, false));
        final PsiTypeParameter newTypeParameter = factory.createTypeParameter(newName, typeParam.getSuperTypes());
        substitutor.put(typeParam, factory.createType(newTypeParameter));
        return newTypeParameter;
      }
    }
    return factory.createTypeParameter(typeParam.getName(), typeParam.getSuperTypes());
  }

  @NotNull
  private static String suggestUniqueTypeParameterName(String baseName, @NotNull PsiTypeParameterList typeParameterList, @Nullable PsiClass targetClass) {
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
      if (Comparing.equal(typeParameter.getName(), baseName)) {
        return false;
      }
    }
    return true;
  }


  @NotNull
  private static PsiTypeParameter substituteTypeParameter(final @NotNull JVMElementFactory factory,
                                                          @NotNull PsiTypeParameter typeParameter,
                                                          final @NotNull PsiSubstitutor substitutor,
                                                          @NotNull final PsiMethod sourceMethod) {
    final PsiElement copy = (typeParameter instanceof PsiCompiledElement ? ((PsiCompiledElement)typeParameter).getMirror() : typeParameter).copy();
    final Map<PsiElement, PsiElement> replacementMap = new HashMap<>();
    copy.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        final PsiElement resolve = reference.resolve();
        if (resolve instanceof PsiTypeParameter) {
          final PsiType type = factory.createType((PsiTypeParameter)resolve);
          replacementMap.put(reference, factory.createReferenceElementByType((PsiClassType)substituteType(substitutor, type, sourceMethod)));
        }
      }
    });
    return (PsiTypeParameter)RefactoringUtil.replaceElementsWithMap(copy, replacementMap);
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

  public static PsiParameter[] overriddenParameters(PsiParameter[] parameters,
                                                    @NotNull JVMElementFactory factory,
                                                    @NotNull JavaCodeStyleManager codeStyleManager,
                                                    @NotNull PsiSubstitutor substitutor,
                                                    PsiElement target) {
    PsiParameter[] result = new PsiParameter[parameters.length];
    UniqueNameGenerator generator = new UniqueNameGenerator();

    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      final PsiType parameterType = parameter.getType();
      final PsiType substituted = substituteType(substitutor, parameterType, (PsiMethod)parameter.getDeclarationScope());
      String paramName = parameter.getName();
      boolean isBaseNameGenerated = true;
      final boolean isSubstituted = substituted.equals(parameterType);
      if (!isSubstituted && isBaseNameGenerated(codeStyleManager, TypeConversionUtil.erasure(parameterType), paramName)) {
        isBaseNameGenerated = false;
      }

      if (paramName == null ||
          isBaseNameGenerated && !isSubstituted && isBaseNameGenerated(codeStyleManager, parameterType, paramName) ||
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
        expressionType = new PsiEllipsisType(((PsiArrayType)expressionType).getComponentType());
      }
      result[i] = factory.createParameter(paramName, expressionType, target);
    }
    return result;
  }

  private static void substituteThrows(@NotNull JVMElementFactory factory,
                                       @NotNull PsiReferenceList targetThrowsList,
                                       @NotNull PsiSubstitutor substitutor,
                                       @NotNull PsiMethod sourceMethod,
                                       List<PsiClassType> thrownTypes) {
    for (PsiClassType thrownType : thrownTypes) {
      targetThrowsList.add(factory.createReferenceElementByType((PsiClassType)substituteType(substitutor, thrownType, sourceMethod)));
    }
  }

  private static void copyDocComment(PsiMethod source, PsiMethod target, JVMElementFactory factory) {
    final PsiElement navigationElement = source.getNavigationElement();
    if (navigationElement instanceof PsiDocCommentOwner) {
      final PsiDocComment docComment = ((PsiDocCommentOwner)navigationElement).getDocComment();
      if (docComment != null) {
        target.addAfter(factory.createDocCommentFromText(docComment.getText()), null);
      }
    }
    final PsiParameter[] sourceParameters = source.getParameterList().getParameters();
    final PsiParameterList targetParameterList = target.getParameterList();
    RefactoringUtil.fixJavadocsForParams(target, new HashSet<>(Arrays.asList(targetParameterList.getParameters())), pair -> {
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
    return factory.createMethod(method.getName(), PsiType.VOID, target);
  }

  private static void substituteReturnType(@NotNull PsiManager manager,
                                           @NotNull PsiMethod method,
                                           @Nullable PsiType returnType,
                                           @NotNull PsiSubstitutor substitutor) {
    final PsiTypeElement returnTypeElement = method.getReturnTypeElement();
    if (returnTypeElement == null || returnType == null) {
      return;
    }
    final PsiType substitutedReturnType = substituteType(substitutor, returnType, method);

    returnTypeElement.replace(new LightTypeElement(manager, substitutedReturnType instanceof PsiWildcardType ? TypeConversionUtil.erasure(substitutedReturnType) : substitutedReturnType));
  }

  @NotNull
  private static JVMElementFactory getFactory(@NotNull Project p, @Nullable PsiElement target) {
    return target == null ? JavaPsiFacade.getInstance(p).getElementFactory() : JVMElementFactories.requireFactory(target.getLanguage(), p);
  }

  private static boolean isBaseNameGenerated(JavaCodeStyleManager csManager, PsiType parameterType, String paramName) {
    if (Arrays.asList(csManager.suggestVariableName(VariableKind.PARAMETER, null, null, parameterType).names).contains(paramName)) {
      return true;
    }
    final String typeName = JavaCodeStyleManagerImpl.getTypeName(parameterType);
    return typeName != null &&
           NameUtil.getSuggestionsByName(typeName, "", "", false, false, parameterType instanceof PsiArrayType).contains(paramName);
  }

  private static PsiType substituteType(final PsiSubstitutor substitutor, final PsiType type, @NotNull PsiTypeParameterListOwner owner) {
    if (PsiUtil.isRawSubstitutor(owner, substitutor)) {
      return TypeConversionUtil.erasure(type);
    }
    final PsiType psiType = substitutor.substitute(type);
    if (psiType != null) {
      final PsiType deepComponentType = psiType.getDeepComponentType();
      if (!(deepComponentType instanceof PsiCapturedWildcardType || deepComponentType instanceof PsiWildcardType)){
        return psiType;
      }
    }
    return TypeConversionUtil.erasure(type);
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

    if (emptyTemplate) {
      OverrideImplementUtil.setupMethodBody(method, overridden, containingClass);
    }
    OverrideImplementUtil.annotateOnOverrideImplement(method, base, overridden);
  }

  /**
   * to be deleted in 2017.2
   */
  @Deprecated
  public static void copyOrReplaceModifierList(@NotNull PsiModifierListOwner sourceParam, @NotNull PsiModifierListOwner targetParam) {
    copyOrReplaceModifierList(sourceParam, null, targetParam);
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
      String qualifiedName = annotation.getQualifiedName();
      if (qualifiedName == null || ArrayUtil.contains(qualifiedName, skipAnnotations) || target.findAnnotation(qualifiedName) != null) {
        continue;
      }
      target.add(annotation);
    }
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
  public static String suggestGetterName(PsiField field) {
    return generateGetterPrototype(field).getName();
  }

  public static String suggestGetterName(String name, PsiType type, Project project) {
    if (type instanceof PsiEllipsisType) {
      type = ((PsiEllipsisType)type).toArrayType();
    }
    return suggestGetterName(JavaPsiFacade.getElementFactory(project).createField(name, type));
  }

  public static String suggestSetterName(PsiField field) {
    return generateSetterPrototype(field).getName();
  }

  public static String suggestSetterName(String name, PsiType type, Project project) {
    if (type instanceof PsiEllipsisType) {
      type = ((PsiEllipsisType)type).toArrayType();
    }
    return suggestSetterName(JavaPsiFacade.getElementFactory(project).createField(name, type));
  }

  public static PsiMethod generateGetterPrototype(@NotNull PsiField field) {
    return generateGetterPrototype(field, true);
  }

  public static PsiMethod generateSetterPrototype(@NotNull PsiField field) {
    return generateSetterPrototype(field, true);
  }

  public static PsiMethod generateSetterPrototype(@NotNull PsiField field, PsiClass aClass) {
    return generatePrototype(field, aClass, true, SetterTemplatesManager.getInstance());
  }

  static PsiMethod generateGetterPrototype(@NotNull PsiField field, boolean ignoreInvalidTemplate) {
    return generatePrototype(field, field.getContainingClass(), ignoreInvalidTemplate, GetterTemplatesManager.getInstance());
  }

  static PsiMethod generateSetterPrototype(@NotNull PsiField field, boolean ignoreInvalidTemplate) {
    return generatePrototype(field, field.getContainingClass(), ignoreInvalidTemplate, SetterTemplatesManager.getInstance());
  }

  private static PsiMethod generatePrototype(@NotNull PsiField field,
                                             PsiClass psiClass,
                                             boolean ignoreInvalidTemplate,
                                             TemplatesManager templatesManager) {
    Project project = field.getProject();
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    String template = templatesManager.getDefaultTemplate().getTemplate();
    String methodText = GenerationUtil.velocityGenerateCode(psiClass, Collections.singletonList(field), new HashMap<>(), template, 0, false);

    boolean isGetter = templatesManager instanceof GetterTemplatesManager;
    PsiMethod result;
    try {
      result = factory.createMethodFromText(methodText, psiClass);
    }
    catch (IncorrectOperationException e) {
      if (ignoreInvalidTemplate) {
        LOG.info(e);
        result = isGetter ? PropertyUtilBase.generateGetterPrototype(field) : PropertyUtilBase.generateSetterPrototype(field);
        assert result != null : field.getText();
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
  private static PsiMethod generatePrototype(@NotNull PsiField field, PsiMethod result) {
    return setVisibility(field, annotateOnOverrideImplement(field.getContainingClass(), result));
  }

  @Contract("_, null -> null")
  public static PsiMethod setVisibility(PsiMember member, PsiMethod prototype) {
    if (prototype == null) return null;

    String visibility = CodeStyleSettingsManager.getSettings(member.getProject()).getCustomSettings(JavaCodeStyleSettings.class).VISIBILITY;

    @PsiModifier.ModifierConstant String newVisibility;
    if (VisibilityUtil.ESCALATE_VISIBILITY.equals(visibility)) {
      PsiClass aClass = member instanceof PsiClass ? (PsiClass)member : member.getContainingClass();
      newVisibility = PsiUtil.getMaximumModifierForMember(aClass, false);
    }
    else {
      //noinspection MagicConstant
      newVisibility = visibility;
    }
    VisibilityUtil.setVisibility(prototype.getModifierList(), newVisibility);

    return prototype;
  }

  @Nullable
  public static PsiMethod annotateOnOverrideImplement(@Nullable PsiClass targetClass, @Nullable PsiMethod generated) {
    if (generated == null || targetClass == null) return generated;

    if (CodeStyleSettingsManager.getSettings(targetClass.getProject()).getCustomSettings(JavaCodeStyleSettings.class).INSERT_OVERRIDE_ANNOTATION) {
      PsiMethod superMethod = targetClass.findMethodBySignature(generated, true);
      if (superMethod != null && superMethod.getContainingClass() != targetClass) {
        OverrideImplementUtil.annotateOnOverrideImplement(generated, targetClass, superMethod, true);
      }
    }
    return generated;
  }
}