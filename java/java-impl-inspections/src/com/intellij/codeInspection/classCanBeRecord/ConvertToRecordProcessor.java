// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.classCanBeRecord;

import com.intellij.codeInspection.RedundantRecordConstructorInspection;
import com.intellij.codeInspection.RedundantRecordConstructorInspection.ConstructorSimplifier;
import com.intellij.codeInspection.classCanBeRecord.ConvertToRecordFix.FieldAccessorCandidate;
import com.intellij.codeInspection.classCanBeRecord.ConvertToRecordFix.RecordCandidate;
import com.intellij.java.library.JavaLibraryUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringFactory;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.SmartHashSet;
import com.siyeh.ig.callMatcher.CallMatcher;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.codeInspection.classCanBeRecord.ConvertToRecordFix.RecordConstructorCandidate;

/**
 * Responsible for converting a single {@link RecordCandidate} that is {@link RecordCandidate#isValid valid}.
 */
final class ConvertToRecordProcessor extends BaseRefactoringProcessor {
  private static final CallMatcher OBJECT_EQUALS = CallMatcher
    .instanceCall(CommonClassNames.JAVA_LANG_OBJECT, "equals")
    .parameterTypes(CommonClassNames.JAVA_LANG_OBJECT);

  private static final CallMatcher OBJECT_HASHCODE = CallMatcher
    .instanceCall(CommonClassNames.JAVA_LANG_OBJECT, "hashCode")
    .parameterCount(0);

  private final RecordCandidate myRecordCandidate;
  private final boolean mySuggestAccessorsRenaming;

  private final Map<PsiElement, String> myAllRenames = new LinkedHashMap<>();

  ConvertToRecordProcessor(@NotNull RecordCandidate recordCandidate, boolean suggestAccessorsRenaming) {
    super(recordCandidate.getProject());
    myRecordCandidate = recordCandidate;
    mySuggestAccessorsRenaming = suggestAccessorsRenaming;
  }

  @Override
  protected @NotNull UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new UsageViewDescriptorAdapter() {
      @Override
      public PsiElement @NotNull [] getElements() {
        return new PsiElement[]{myRecordCandidate.getPsiClass()};
      }

      @Override
      public String getProcessedElementsHeader() {
        return JavaRefactoringBundle.message("convert.to.record.title");
      }
    };
  }

  @Override
  protected void doRun() {
    prepareRenameOfAccessors();
    prepareRenameOfConstructorParameters();

    super.doRun();
  }

  private void prepareRenameOfConstructorParameters() {
    RecordConstructorCandidate ctorCandidate = myRecordCandidate.getCanonicalConstructorCandidate();
    if (ctorCandidate == null) return;

    ctorCandidate.getCtorParamsToFields().forEach((ctorParam, field) -> {
      if (!ctorParam.getName().equals(field.getName())) {
        RenameRefactoring renameRefactoring = RefactoringFactory.getInstance(myProject).createRename(ctorParam, field.getName());
        renameRefactoring.setPreviewUsages(false);
        renameRefactoring.setSearchInComments(false);
        // The below line is required to not show conflicts midway and break the refactoring flow.
        renameRefactoring.setSearchInNonJavaFiles(false);
        renameRefactoring.setInteractive(null);
        renameRefactoring.run();
      }
    });
  }

  private void prepareRenameOfAccessors() {
    List<FieldAccessorCandidate> accessorsToRename = getAccessorsToRename();

    for (var fieldAccessorCandidate : accessorsToRename) {
      String backingFieldName = fieldAccessorCandidate.backingField().getName();

      List<PsiMethod> methods = substituteWithSuperMethodsIfPossible(fieldAccessorCandidate.method());
      RenamePsiElementProcessor methodRenameProcessor = RenamePsiElementProcessor.forElement(methods.get(0));

      methods.forEach(method -> {
        myAllRenames.put(method, backingFieldName);
        methodRenameProcessor.prepareRenaming(method, backingFieldName, myAllRenames);
      });
    }
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    List<UsageInfo> usages = new SmartList<>();
    for (var psiField : myRecordCandidate.getFieldsToAccessorCandidates().keySet()) {
      if (!psiField.hasModifierProperty(PsiModifier.PRIVATE)) {
        for (PsiReference reference : ReferencesSearch.search(psiField).findAll()) {
          usages.add(new FieldUsageInfo(psiField, reference));
        }
      }
    }

    List<FieldAccessorCandidate> accessorsToRename = getAccessorsToRename();

    for (var fieldAccessorCandidate : accessorsToRename) {
      String backingFieldName = fieldAccessorCandidate.backingField().getName();

      List<PsiMethod> methods = substituteWithSuperMethodsIfPossible(fieldAccessorCandidate.method());
      methods.forEach(method -> {
        UsageInfo[] methodUsages = RenameUtil.findUsages(method, backingFieldName, false, false, myAllRenames);
        usages.addAll(Arrays.asList(methodUsages));
      });
    }

    for (var fieldAccessorCandidate : accessorsToRename) {
      String backingFieldName = fieldAccessorCandidate.backingField().getName();
      usages.add(new RenameMethodUsageInfo(fieldAccessorCandidate.method(), backingFieldName));
    }

    usages.addAll(findConflicts(myRecordCandidate));
    return usages.toArray(UsageInfo.EMPTY_ARRAY);
  }

  /**
   * @return list of accessors whose names aren't record-compatible and need to be renamed separately.
   */
  private @NotNull @Unmodifiable List<@NotNull FieldAccessorCandidate> getAccessorsToRename() {
    List<FieldAccessorCandidate> list = ContainerUtil.filter(
      myRecordCandidate.getFieldsToAccessorCandidates().values(),
      fieldAccessorCandidate -> fieldAccessorCandidate != null && !fieldAccessorCandidate.usesRecordStyleNaming()
    );
    return list;
  }

  /**
   * @param accessor a declaration to find supers methods for
   * @return a list of direct super methods, or the declaration itself if no super methods are found
   */
  private static @NotNull @Unmodifiable List<@NotNull PsiMethod> substituteWithSuperMethodsIfPossible(@NotNull PsiMethod accessor) {
    PsiMethod[] superMethods = accessor.findSuperMethods();
    if (superMethods.length == 0) {
      return List.of(accessor);
    }
    else {
      return List.of(superMethods);
    }
  }

  static @NotNull List<UsageInfo> findConflicts(@NotNull RecordCandidate recordCandidate) {
    List<UsageInfo> result = new SmartList<>();
    for (var entry : recordCandidate.getFieldsToAccessorCandidates().entrySet()) {
      PsiField psiField = entry.getKey();
      FieldAccessorCandidate fieldAccessorCandidate = entry.getValue();
      if (fieldAccessorCandidate == null) {
        if (firstHasWeakerAccess(recordCandidate.getPsiClass(), psiField)) {
          result.add(new BrokenEncapsulationUsageInfo(psiField, JavaRefactoringBundle
            .message("convert.to.record.accessor.more.accessible",
                     StringUtil.capitalize(RefactoringUIUtil.getDescription(psiField, false)),
                     VisibilityUtil.getVisibilityStringToDisplay(psiField),
                     VisibilityUtil.toPresentableText(PsiModifier.PUBLIC))));
        }
      }
      else {
        PsiMethod accessor = fieldAccessorCandidate.method();
        if (firstHasWeakerAccess(recordCandidate.getPsiClass(), accessor)) {
          result.add(new BrokenEncapsulationUsageInfo(accessor, JavaRefactoringBundle
            .message("convert.to.record.accessor.more.accessible",
                     StringUtil.capitalize(RefactoringUIUtil.getDescription(accessor, false)),
                     VisibilityUtil.getVisibilityStringToDisplay(accessor),
                     VisibilityUtil.toPresentableText(PsiModifier.PUBLIC))));
        }
      }
    }
    RecordConstructorCandidate canonicalCtorCandidate = recordCandidate.getCanonicalConstructorCandidate();
    if (canonicalCtorCandidate != null) {
      PsiMethod canonicalCtor = canonicalCtorCandidate.getConstructorMethod();
      if (firstHasWeakerAccess(recordCandidate.getPsiClass(), canonicalCtor)) {
        result.add(new BrokenEncapsulationUsageInfo(canonicalCtor, JavaRefactoringBundle
          .message("convert.to.record.ctor.more.accessible",
                   StringUtil.capitalize(RefactoringUIUtil.getDescription(canonicalCtor, false)),
                   VisibilityUtil.getVisibilityStringToDisplay(canonicalCtor),
                   VisibilityUtil.getVisibilityStringToDisplay(recordCandidate.getPsiClass()))));
      }
    }
    return result;
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    final UsageInfo[] usages = refUsages.get();
    MultiMap<PsiElement, @NlsContexts.DialogMessage String> conflicts = new MultiMap<>();
    RenameUtil.addConflictDescriptions(usages, conflicts);
    for (UsageInfo usage : usages) {
      if (usage instanceof BrokenEncapsulationUsageInfo) {
        conflicts.putValue(usage.getElement(), ((BrokenEncapsulationUsageInfo)usage).myErrMsg);
      }
      else if (usage instanceof FieldUsageInfo fieldUsageInfo) {
        PsiElement element = fieldUsageInfo.getElement();
        if (element != null && !isAccessible(element, fieldUsageInfo.myField)) {
          boolean canBeFixed = element instanceof PsiReferenceExpression refExpr && !PsiUtil.isAccessedForWriting(refExpr);
          if (!canBeFixed) {
            final PsiElement container = ConflictsUtil.getContainer(element);
            String message = JavaRefactoringBundle.message("0.will.become.inaccessible.from.1",
                                                           RefactoringUIUtil.getDescription(fieldUsageInfo.myField, true),
                                                           RefactoringUIUtil.getDescription(container, true));
            conflicts.putValue(element, message);
          }
        }
      }
      else if (usage instanceof RenameMethodUsageInfo renameMethodInfo) {
        RenamePsiElementProcessor renameMethodProcessor = RenamePsiElementProcessor.forElement(renameMethodInfo.myMethod);
        renameMethodProcessor.findExistingNameConflicts(renameMethodInfo.myMethod, renameMethodInfo.myNewName, conflicts, myAllRenames);
      }
    }

    if (!conflicts.isEmpty() && ApplicationManager.getApplication().isUnitTestMode()) {
      if (!ConflictsInTestsException.isTestIgnore()) {
        throw new ConflictsInTestsException(conflicts.values());
      }
      return true;
    }

    return showConflicts(conflicts, usages);
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    renameMembers(usages);

    final PsiClass psiClass = myRecordCandidate.getPsiClass();
    final RecordConstructorCandidate canonicalCtorCandidate = myRecordCandidate.getCanonicalConstructorCandidate();
    final Map<PsiField, FieldAccessorCandidate> fieldToAccessorCandidateMap = myRecordCandidate.getFieldsToAccessorCandidates();
    RecordBuilder recordBuilder = new RecordBuilder(psiClass);
    PsiIdentifier classIdentifier = null;
    PsiElement nextElement = psiClass.getFirstChild();
    while (nextElement != null) {
      if (nextElement instanceof PsiKeyword && JavaTokenType.CLASS_KEYWORD.equals(((PsiKeyword)nextElement).getTokenType())) {
        recordBuilder.addRecordDeclaration();
      }
      else if (nextElement instanceof PsiIdentifier) {
        classIdentifier = (PsiIdentifier)nextElement;
        recordBuilder.addPsiElement(classIdentifier);
      }
      else if (nextElement instanceof PsiTypeParameterList) {
        recordBuilder.addPsiElement(nextElement);
        if (PsiTreeUtil.skipWhitespacesAndCommentsBackward(nextElement) == classIdentifier) {
          recordBuilder.addRecordHeader(canonicalCtorCandidate, fieldToAccessorCandidateMap);
          classIdentifier = null;
        }
      }
      else if (nextElement instanceof PsiModifierList modifierList) {
        recordBuilder.addModifierList(modifierList);
      }
      else if (nextElement instanceof PsiField psiField) {
        psiField.normalizeDeclaration();
        if (fieldToAccessorCandidateMap.containsKey(psiField)) {
          nextElement = PsiTreeUtil.skipWhitespacesForward(nextElement);
          continue;
        }
        recordBuilder.addPsiElement(nextElement);
      }
      else if (nextElement instanceof PsiMethod psiMethod) {
        if (canonicalCtorCandidate != null && psiMethod == canonicalCtorCandidate.getConstructorMethod()) {
          recordBuilder.addCanonicalCtor(canonicalCtorCandidate.getConstructorMethod());
        }
        else {
          FieldAccessorCandidate fieldAccessorCandidate = getFieldAccessorCandidate(fieldToAccessorCandidateMap, psiMethod);
          if (fieldAccessorCandidate == null) {
            recordBuilder.addPsiElement(psiMethod);
          }
          else {
            recordBuilder.addFieldAccessor(fieldAccessorCandidate);
          }
        }
      }
      else {
        recordBuilder.addPsiElement(nextElement);
      }
      nextElement = nextElement.getNextSibling();
    }

    Set<PsiMethod> syntheticGetters = Arrays.stream(psiClass.getMethods())
      .filter(method -> method instanceof SyntheticElement)
      .filter(method -> ContainerUtil.exists(fieldToAccessorCandidateMap.keySet(),
                                             field -> field.getName().equals(PropertyUtilBase.getPropertyNameByGetter(method))))
      .collect(Collectors.toSet());

    useAccessorsWhenNecessary(usages);
    CallMatcher redundantObjectMethods = findRedundantObjectMethods(myRecordCandidate);
    PsiClass result = (PsiClass)psiClass.replace(recordBuilder.build());
    tryToCompactCanonicalCtor(result);
    removeRedundantObjectMethods(result, redundantObjectMethods);
    addImplicitLombokGetters(result, syntheticGetters.toArray(PsiMethod.EMPTY_ARRAY));
    removeRedundantLombokAnnotations(result);
    generateJavaDocForDocumentedFields(result, myRecordCandidate.getFieldsToAccessorCandidates().keySet());
    CodeStyleManager.getInstance(myProject).reformat(JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(result));
  }

  private void addImplicitLombokGetters(@NotNull PsiClass record, @NotNull PsiMethod @NotNull [] implicitGetters) {
    if (!mySuggestAccessorsRenaming) {
      PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
      for (PsiMethod getter : implicitGetters) {
        record.add(elementFactory.createMethodFromText(getter.getText(), record));
      }
    }
  }

  private void useAccessorsWhenNecessary(@NotNull UsageInfo @NotNull [] usages) {
    for (UsageInfo usage : usages) {
      if (usage instanceof FieldUsageInfo fieldUsageInfo) {
        PsiField field = fieldUsageInfo.myField;
        PsiElement target = fieldUsageInfo.getElement();
        if (target instanceof PsiReferenceExpression refExpr && !PsiUtil.isAccessedForWriting(refExpr) && !isAccessible(target, field)) {
          refExpr.replace(JavaPsiFacade.getElementFactory(myProject).createExpressionFromText(refExpr.getText() + "()", refExpr));
        }
        if (target instanceof PsiDocTagValue docTagValue) {
          PsiDocTag docTag = JavaPsiFacade.getElementFactory(myProject).createDocTagFromText("@see " + docTagValue.getText() + "()");
          docTagValue.replace(Objects.requireNonNull(docTag.getValueElement()));
        }
      }
    }
  }

  private boolean isAccessible(@NotNull PsiElement place, @NotNull PsiField psiField) {
    return JavaPsiFacade.getInstance(myProject).getResolveHelper()
      .isAccessible(psiField, new LightModifierList(psiField.getManager(), psiField.getLanguage(), PsiModifier.PRIVATE),
                    place, null, null);
  }

  private void renameMembers(UsageInfo @NotNull [] usages) {
    List<UsageInfo> renameUsages = ContainerUtil.filter(usages, u -> !(u instanceof ConvertToRecordUsageInfo));
    MultiMap<PsiElement, UsageInfo> renameUsagesByElement = RenameProcessor.classifyUsages(myAllRenames.keySet(), renameUsages);
    for (var entry : myAllRenames.entrySet()) {
      PsiElement element = entry.getKey();
      String newName = entry.getValue();
      UsageInfo[] elementRenameUsages = renameUsagesByElement.get(entry.getKey()).toArray(UsageInfo.EMPTY_ARRAY);
      RenamePsiElementProcessor.forElement(element).renameElement(element, newName, elementRenameUsages, null);
    }
  }

  /**
   * Finds methods within {@code recordCandidate} that are redundant implementations of {@link Object#equals} and {@link Object#hashCode}.
   */
  private static CallMatcher findRedundantObjectMethods(@NotNull RecordCandidate recordCandidate) {
    PsiMethod equalsMethod = recordCandidate.getEqualsMethod();
    PsiMethod hashCodeMethod = recordCandidate.getHashCodeMethod();
    if (equalsMethod == null && hashCodeMethod == null) return CallMatcher.none();
    List<CallMatcher> result = new SmartList<>();
    Set<PsiField> fields = recordCandidate.getFieldsToAccessorCandidates().keySet();
    if (EqualsChecker.isStandardEqualsMethod(equalsMethod, fields)) {
      result.add(OBJECT_EQUALS);
    }
    if (hashCodeMethod != null) {
      var hashCodeVisitor = new HashCodeVisitor(fields);
      hashCodeMethod.accept(hashCodeVisitor);
      if (hashCodeVisitor.myNonVisitedFields.isEmpty()) {
        result.add(OBJECT_HASHCODE);
      }
    }
    return CallMatcher.anyOf(result.toArray(new CallMatcher[0]));
  }

  private static class HashCodeVisitor extends JavaRecursiveElementWalkingVisitor {
    private final CallMatcher OBJECTS_CALL = CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_OBJECTS, "hash", "hashCode");
    private final CallMatcher FLOAT_CALL = CallMatcher.staticCall(CommonClassNames.JAVA_LANG_FLOAT, "floatToIntBits");
    private final CallMatcher DOUBLE_CALL = CallMatcher.staticCall(CommonClassNames.JAVA_LANG_DOUBLE, "doubleToLongBits");

    private final CallMatcher INSTANCE_CALL = CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_OBJECT, "hashCode");

    private final Set<PsiField> myNonVisitedFields;

    private HashCodeVisitor(@NotNull Set<PsiField> fields) {
      myNonVisitedFields = new SmartHashSet<>(fields);
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      PsiField field = ObjectUtils.tryCast(expression.resolve(), PsiField.class);
      if (field == null) return;
      PsiType fieldType = field.getType();
      if (PsiTypes.charType().equals(fieldType) || PsiTypes.shortType().equals(fieldType)) {
        PsiElement parent = PsiTreeUtil.skipParentsOfType(expression, PsiParenthesizedExpression.class);
        if (parent instanceof PsiTypeCastExpression) {
          PsiTypeElement castType = ((PsiTypeCastExpression)parent).getCastType();
          if (castType != null && PsiTypes.intType().equals(castType.getType())) {
            myNonVisitedFields.remove(field);
          }
        }
      }
      else {
        myNonVisitedFields.remove(field);
      }
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      if (OBJECTS_CALL.test(expression)) {
        for (PsiExpression argument : expression.getArgumentList().getExpressions()) {
          PsiReferenceExpression fieldRef = ObjectUtils.tryCast(argument, PsiReferenceExpression.class);
          if (fieldRef == null || !myNonVisitedFields.remove(fieldRef.resolve())) {
            stopWalking();
            return;
          }
        }
        return;
      }

      PsiType expectedType = null;
      if (FLOAT_CALL.test(expression)) {
        expectedType = PsiTypes.floatType();
      }
      else if (DOUBLE_CALL.test(expression)) {
        expectedType = PsiTypes.doubleType();
      }
      if (expectedType != null) {
        PsiExpression[] expressions = expression.getArgumentList().getExpressions();
        if (expressions.length == 1) {
          PsiReferenceExpression refExpr = ObjectUtils.tryCast(expressions[0], PsiReferenceExpression.class);
          if (refExpr != null) {
            PsiField field = ObjectUtils.tryCast(refExpr.resolve(), PsiField.class);
            if (field != null && expectedType.equals(field.getType())) {
              myNonVisitedFields.remove(field);
              return;
            }
          }
        }
        stopWalking();
        return;
      }

      if (INSTANCE_CALL.test(expression)) {
        PsiExpression qualifier = expression.getMethodExpression().getQualifierExpression();
        PsiReferenceExpression fieldRef = ObjectUtils.tryCast(qualifier, PsiReferenceExpression.class);
        if (fieldRef != null) {
          PsiField field = ObjectUtils.tryCast(fieldRef.resolve(), PsiField.class);
          if (field != null) {
            myNonVisitedFields.remove(field);
            return;
          }
        }
      }
      stopWalking();
    }

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      IElementType operationType = expression.getOperationTokenType();
      if (!JavaTokenType.PLUS.equals(operationType) && !JavaTokenType.MINUS.equals(operationType) &&
          !JavaTokenType.ASTERISK.equals(operationType) && !JavaTokenType.GTGTGT.equals(operationType) &&
          !JavaTokenType.EQEQ.equals(operationType) && !JavaTokenType.NE.equals(operationType)) {
        stopWalking();
      }
    }
  }

  private static void tryToCompactCanonicalCtor(@NotNull PsiClass record) {
    if (!record.isRecord()) throw new IllegalArgumentException("Not a record: " + record);

    PsiMethod canonicalCtor = ArrayUtil.getFirstElement(record.getConstructors());
    if (canonicalCtor != null) {
      PsiCodeBlock ctorBody = canonicalCtor.getBody();
      if (ctorBody != null) {
        StreamEx.of(ctorBody.getStatements()).select(PsiExpressionStatement.class)
          .filter(st -> JavaPsiConstructorUtil.isSuperConstructorCall(st.getExpression()))
          .findFirst()
          .ifPresent(st -> st.delete());
      }
      ConstructorSimplifier ctorSimplifier = RedundantRecordConstructorInspection.createCtorSimplifier(canonicalCtor);
      if (ctorSimplifier != null) {
        ctorSimplifier.simplify(canonicalCtor);
      }
    }
  }

  private static void removeRedundantObjectMethods(@NotNull PsiClass record, @NotNull CallMatcher redundantObjectMethods) {
    ContainerUtil.filter(record.getMethods(), redundantObjectMethods::methodMatches)
      .forEach(PsiMethod::delete);
  }

  /**
   * This could be also implemented as a {@link com.intellij.refactoring.RefactoringHelper}.
   * {@link BaseRefactoringProcessor} automatically calls refactoring helpers after refactoring is done.
   * <p>
   * The problem with it is that refactoring helpers don't trivially work in preview, so we would make the bug IDEA-369873 only worse.
   */
  private static void removeRedundantLombokAnnotations(@NotNull PsiClass record) {
    if (!record.isRecord()) throw new IllegalArgumentException("Not a record: " + record);
    if (!JavaLibraryUtil.hasLibraryJar(record.getProject(), "org.projectlombok:lombok")) return;

    // Remove annotations from the class
    for (final PsiAnnotation annotation : record.getAnnotations()) {
      final String qualifiedName = annotation.getQualifiedName();
      if (qualifiedName == null) continue;
      if (Set.of(
        "lombok.ToString",
        "lombok.Getter",
        "lombok.EqualsAndHashCode",
        "lombok.RequiredArgsConstructor",
        "lombok.Data",
        "lombok.Value").contains(qualifiedName)) {
        annotation.delete();
      }
    }

    // Remove annotations from instance fields
    for (final PsiField field : record.getFields()) {
      for (final PsiAnnotation annotation : field.getAnnotations()) {
        final String qualifiedName = annotation.getQualifiedName();
        if (qualifiedName != null && qualifiedName.equals("lombok.Getter")) {
          annotation.delete();
        }
      }
    }
  }

  private static void generateJavaDocForDocumentedFields(@NotNull PsiClass record, @NotNull Set<@NotNull PsiField> fields) {
    Map<String, String> comments = new LinkedHashMap<>();
    for (PsiField field : fields) {
      StringBuilder fieldComment = new StringBuilder();
      for (PsiComment comment : ObjectUtils.notNull(PsiTreeUtil.getChildrenOfType(field, PsiComment.class), new PsiComment[0])) {
        if (comment instanceof PsiDocComment) {
          Arrays.stream(((PsiDocComment)comment).getDescriptionElements()).map(PsiElement::getText).forEach(fieldComment::append);
          continue;
        }
        String commentText = comment.getText();
        String unwrappedText = comment.getTokenType() == JavaTokenType.END_OF_LINE_COMMENT
                               ? StringUtil.trimStart(commentText, "//")
                               : StringUtil.trimEnd(commentText.substring(2), "*/");
        fieldComment.append(unwrappedText);
      }
      if (!fieldComment.isEmpty()) {
        comments.put(field.getName(), fieldComment.toString());
      }
    }
    if (comments.isEmpty()) return;
    PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(record.getProject()).getParserFacade();
    PsiDocComment recordDoc = record.getDocComment();
    if (recordDoc == null) {
      PsiDocComment emptyDoc = parserFacade.createDocCommentFromText("/** */");
      recordDoc = (PsiDocComment)record.addBefore(emptyDoc, record.getFirstChild());
    }
    for (var entry : comments.entrySet()) {
      String paramName = entry.getKey();
      String paramComment = entry.getValue();
      PsiDocTag docTag = parserFacade.createDocTagFromText("@param " + paramName + " " + paramComment);
      recordDoc.add(docTag);
    }
  }

  @Override
  protected @NotNull @NlsContexts.Command String getCommandName() {
    return JavaRefactoringBundle.message("convert.to.record.title");
  }

  private static boolean firstHasWeakerAccess(@NotNull PsiModifierListOwner first, @NotNull PsiModifierListOwner second) {
    return VisibilityUtil.compare(VisibilityUtil.getVisibilityModifier(first.getModifierList()),
                                  VisibilityUtil.getVisibilityModifier(second.getModifierList())) < 0;
  }

  private static @Nullable FieldAccessorCandidate getFieldAccessorCandidate(@NotNull Map<PsiField, @Nullable FieldAccessorCandidate> fieldAccessors,
                                                                            @NotNull PsiMethod psiMethod) {
    return ContainerUtil.find(fieldAccessors.values(), value -> value != null && psiMethod.equals(value.method()));
  }
}
