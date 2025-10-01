// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.DataFlowInspectionBase;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.nullable.NotNullFieldNotInitializedInspection;
import com.intellij.codeInspection.nullable.NullableStuffInspection;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.LineColumn;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.testFramework.core.FileComparisonFailedError;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The goal of this test is to check that IDEA supports popular cases of JSpecify annotations and doesn't have regressions.
 * This test contains a set of filters, which are used to filter out some cases that are not supported by IDEA or aren't expected to be supported.
 */
@RunWith(Parameterized.class)
public class JSpecifyFilteredAnnotationTest extends LightJavaCodeInsightFixtureTestCase {

  private static final String PACKAGE_NAME = "org.jspecify.annotations";
  private static final Path PATH = Paths.get(JavaTestUtil.getJavaTestDataPath(), "/inspection/dataFlow/jspecify/");
  private static final Pattern JSPECIFY_PATTERN = Pattern.compile("// jspecify_\\w+");
  private static final Pattern TEST_CANNOT_CONVERT = Pattern.compile("// test:cannot-convert.*!>?");
  private static final Statistic STATISTIC = new Statistic(new LongAdder(), new LongAdder(), new LongAdder(), MultiMap.create());

  @Parameterized.Parameter
  public String myFileName;

  private static final List<ErrorFilter> FILTERS = List.of(
    new SkipErrorFilter("jspecify_nullness_not_enough_information"), //it is useless for our goals
    new ReturnSynchronizedWithUnspecifiedFilter(), // it looks like it is useless because @Unspecified is not supported
    new SkipIndividuallyFilter( //each case has its own reason (line number starts from 0)
      Set.of(
        new Pair<>("ContravariantReturns.java", 32),  // see: IDEA-377687
        new Pair<>("ContravariantReturns.java", 36),  // see: IDEA-377687
        new Pair<>("ExtendsTypeVariableImplementedForNullableTypeArgument.java",
                   28), // overriding method with @NotNull, original has @Nullable, but IDEA doesn't highlight the opposite example, see IDEA-377687
        new Pair<>("ExtendsTypeVariableImplementedForNullableTypeArgument.java",
                   33), // overriding method with @NotNull, original has @Nullable, but IDEA doesn't highlight the opposite example, see IDEA-377687
        new Pair<>("OverrideParameters.java", 66),  // see: IDEA-377687

        new Pair<>("DereferenceTypeVariable.java", 117),  // see: IDEA-377688
        new Pair<>("TypeVariableToObject.java", 104), // see: IDEA-377688

        new Pair<>("NullLiteralToTypeVariable.java", 58), // see: IDEA-377691
        new Pair<>("NullLiteralToTypeVariable.java", 78), // see: IDEA-377691
        new Pair<>("NullLiteralToTypeVariable.java", 98), // see: IDEA-377691
        new Pair<>("NullLiteralToTypeVariable.java", 103), // see: IDEA-377691
        new Pair<>("NullLiteralToTypeVariable.java", 118), // see: IDEA-377691
        new Pair<>("TypeVariableUnionNullToParent.java", 88), // see: IDEA-377691
        new Pair<>("TypeVariableUnionNullToParent.java", 98), // see: IDEA-377691
        new Pair<>("TypeVariableUnionNullToSelf.java", 58), // see: IDEA-377691
        new Pair<>("TypeVariableUnionNullToSelf.java", 78), // see: IDEA-377691
        new Pair<>("TypeVariableUnionNullToSelf.java", 103), // see: IDEA-377691
        new Pair<>("TypeVariableUnionNullToSelf.java", 118), // see: IDEA-377691
        new Pair<>("TypeVariableToParent.java", 94), // see: IDEA-377691

        new Pair<>("SuperObject.java", 31),  // see: IDEA-377694
        new Pair<>("SuperTypeVariable.java", 28),  // see: IDEA-377694
        new Pair<>("SuperTypeVariable.java", 57),  // see: IDEA-377694
        new Pair<>("SuperTypeVariable.java", 86),  // see: IDEA-377694
        new Pair<>("SuperTypeVariable.java", 115), // see: IDEA-377694

        new Pair<>("UninitializedField.java", 29), // see: IDEA-377695

        new Pair<>("ContainmentExtends.java", 27),  // see: IDEA-377696
        new Pair<>("ContainmentSuper.java", 36),  // see: IDEA-377696
        new Pair<>("ContainmentSuperVsExtends.java", 22),  // see: IDEA-377696
        new Pair<>("ContainmentSuperVsExtendsSameType.java", 21),  // see: IDEA-377696

        new Pair<>("MultiBoundTypeVariableUnionNullToSelf.java", 62), // see: IDEA-377697

        new Pair<>("WildcardCapturesToBoundOfTypeParameterNotToTypeVariableItself.java", 24), // see: IDEA-377699

        new Pair<>("SelfType.java", 34),  // see: IDEA-377707 (also see the commented case in warning matchers)
        new Pair<>("SelfType.java", 43),  // see: IDEA-377707 (also see the commented case in warning matchers)
        new Pair<>("OutOfBoundsTypeVariable.java", 21)  // see: IDEA-377707 (also see the commented case in warning matchers)
      )
    ),
    new SkipIndividuallyFilter( //cases to investigate later (with unspecified annotation and complicated to understand). (line number starts from 0)
      Set.of(
        new Pair<>("NullLiteralToTypeVariable.java", 53), //IDEA-380143
        new Pair<>("NullLiteralToTypeVariable.java", 73), //IDEA-380143
        new Pair<>("NullLiteralToTypeVariable.java", 83), //IDEA-380143
        new Pair<>("NullLiteralToTypeVariable.java", 93), //IDEA-380143
        new Pair<>("NullLiteralToTypeVariable.java", 113), //IDEA-380143
        new Pair<>("TypeVariableToObject.java", 109), //IDEA-380143
        new Pair<>("TypeVariableToParent.java", 80), //IDEA-380143
        new Pair<>("TypeVariableUnionNullToParent.java", 73), //IDEA-380143
        new Pair<>("TypeVariableUnionNullToParent.java", 78), //IDEA-380143
        new Pair<>("TypeVariableUnionNullToParent.java", 83), //IDEA-380143
        new Pair<>("TypeVariableUnionNullToParent.java", 93), //IDEA-380143
        new Pair<>("TypeVariableUnionNullToSelf.java", 53), //IDEA-380143
        new Pair<>("TypeVariableUnionNullToSelf.java", 73), //IDEA-380143
        new Pair<>("TypeVariableUnionNullToSelf.java", 83), //IDEA-380143
        new Pair<>("TypeVariableUnionNullToSelf.java", 93), //IDEA-380143
        new Pair<>("TypeVariableUnionNullToSelf.java", 98), //IDEA-380143
        new Pair<>("TypeVariableUnionNullToSelf.java", 113), //IDEA-380143
        new Pair<>("TypeVariableUnspecToObject.java", 58), //IDEA-380143
        new Pair<>("TypeVariableUnspecToObject.java", 78), //IDEA-380143
        new Pair<>("TypeVariableUnspecToObject.java", 98), //IDEA-380143
        new Pair<>("TypeVariableUnspecToObject.java", 103), //IDEA-380143
        new Pair<>("TypeVariableUnspecToObject.java", 108), //IDEA-380143
        new Pair<>("TypeVariableUnspecToObject.java", 113), //IDEA-380143
        new Pair<>("TypeVariableUnspecToObject.java", 118), //IDEA-380143
        new Pair<>("TypeVariableUnspecToParent.java", 53), //IDEA-380143
        new Pair<>("TypeVariableUnspecToParent.java", 68), //IDEA-380143
        new Pair<>("TypeVariableUnspecToParent.java", 83), //IDEA-380143
        new Pair<>("TypeVariableUnspecToParent.java", 98), //IDEA-380143

        new Pair<>("DereferenceTypeVariable.java", 123),
        new Pair<>("MultiBoundTypeVariableToObject.java", 43),
        new Pair<>("MultiBoundTypeVariableToObject.java", 52),
        new Pair<>("MultiBoundTypeVariableToOther.java", 43),
        new Pair<>("MultiBoundTypeVariableToOther.java", 52),
        new Pair<>("MultiBoundTypeVariableUnionNullToSelf.java", 42),
        new Pair<>("MultiBoundTypeVariableUnionNullToSelf.java", 47),
        new Pair<>("MultiBoundTypeVariableUnionNullToSelf.java", 57),
        new Pair<>("MultiBoundTypeVariableUnspecToObject.java", 63),
        new Pair<>("MultiBoundTypeVariableUnspecToOther.java", 63),
        new Pair<>("UnionTypeArgumentWithUseSite.java", 95)
      )
    ) {
      @Override
      public boolean shouldCount() {
        return false;
      }
    },
    new CallWithParameterWithNestedGenericsFilter(), // see: IDEA-377682
    new VariableWithNestedGenericsFilter(), // see: IDEA-377683
    new ReturnWithNestedGenericsFilter() // see: IDEA-375132
  );

  private static final LightProjectDescriptor PROJECT_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public Sdk getSdk() {
      return IdeaTestUtil.getMockJdk18();
    }

    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(LanguageLevel.JDK_1_8);
    }
  };

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    // Cannot share project descriptor with previous test, as NullableNotNullManager caches the supported annotations
    // so it won't be updated after Registry.setValue()
    return PROJECT_DESCRIPTOR;
  }

  @Before
  public void before() {
    mockAnnotations();
  }

  private void mockAnnotations() {
    String template = "package " + PACKAGE_NAME + ";import java.lang.annotation.*;\n\n@Target(%s)public @interface %s {}";
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE_USE", "NonNull"));
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE_USE", "Nullable"));
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE_USE", "NullnessUnspecified"));
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE, ElementType.PACKAGE", "NullMarked"));
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE, ElementType.PACKAGE", "NullUnmarked"));
  }

  @Test
  public void test() throws IOException {
    Path path = PATH.resolve(myFileName);
    boolean dirMode = Files.isDirectory(path);
    List<Path> files = Files.walk(path)
      .filter(p -> Files.isRegularFile(p)).filter(p -> p.getFileName().toString().endsWith(".java"))
      .toList();
    if (files.isEmpty()) {
      throw new IllegalStateException("No Java files");
    }
    List<FileData> fileData = new ArrayList<>();
    for (Path file : files) {
      String fileText = Files.readString(file).replace("\r\n", "\n");
      fileText = TEST_CANNOT_CONVERT.matcher(fileText).replaceAll("// jspecify_nullness_mismatch");
      String stripped = JSPECIFY_PATTERN.matcher(fileText).replaceAll("");
      String relativeFile = FileUtil.toSystemIndependentName((dirMode ? path.relativize(file) : file.getFileName()).toString());
      PsiFile psiFile = myFixture.addFileToProject(relativeFile, stripped);
      fileData.add(new FileData(file, fileText, stripped, psiFile, createErrorContainer(fileText)));
    }
    for (FileData data : fileData) {
      String fileText = getExpectedTest(data);
      String stripped = data.stripped;
      PsiFile file = data.psiFile;

      Map<PsiElement, String> actual = new LinkedHashMap<>();
      var dfaInspection = new JSpecifyDataFlowInspection(actual);
      dfaInspection.TREAT_UNKNOWN_MEMBERS_AS_NULLABLE = true;
      var nullableStuffInspection = new JSpecifyNullableStuffInspection(actual);
      var notNullFieldNotInitializedInspection = new JSpecifyNotNullFieldNotInitializedInspection(actual);
      List<LocalInspectionTool> inspections = List.of(dfaInspection, nullableStuffInspection, notNullFieldNotInitializedInspection);
      ReadAction.run(() -> {
        ProblemsHolder holder = new ProblemsHolder(new InspectionManagerEx(getProject()), file, false);
        for (LocalInspectionTool inspection : inspections) {
          PsiElementVisitor visitor = inspection.buildVisitor(holder, false);
          PsiTreeUtil.processElements(file, e -> {
            e.accept(visitor);
            return true;
          });
        }
        String actualText = getActualText(actual, stripped);
        if (!FILTERS.isEmpty()) {
          Assert.assertEquals("Messages don't match (" + data.path.getFileName().toString() + ")", fileText, actualText);
        }
        if (FILTERS.isEmpty() && !fileText.equals(actualText)) {
          throw new FileComparisonFailedError("Messages don't match (" + data.path.getFileName().toString() + ")",
                                              fileText, actualText, data.path.toString());
        }
      });
    }
  }

  String getActualText(Map<PsiElement, String> actual, String stripped) {
    StringBuilder sb = new StringBuilder();
    int pos = 0;
    TreeMap<Integer, List<String>> map = EntryStream.of(actual)
      .filter(e ->
                !ContainerUtil.exists(FILTERS, filter ->
                  filter.filterExpected(e.getKey(), e.getValue())))
      .mapKeys(e -> e.getTextRange().getStartOffset())
      .grouping(TreeMap::new, Collectors.toList());
    for (String str : stripped.split("\n", -1)) {
      int endPos = pos + str.length() + 1;
      String warnings = StreamEx.of(map.subMap(pos, endPos).values()).flatMap(List::stream).distinct().joining(" & ");
      if (!warnings.isEmpty()) {
        sb.append("// ").append(warnings);
      }
      if (!sb.isEmpty()) sb.append("\n");
      pos = endPos;
      sb.append(str);
    }
    return sb.toString();
  }

  @AfterClass
  public static void reportUnusedFilters() {
    FILTERS.forEach(ErrorFilter::reportUnused);
  }

  @AfterClass
  public static void afterClass() {
    System.out.println(STATISTIC);
  }

  @Parameterized.Parameters(name = "{0}")
  public static List<String> getData() throws IOException {
    return Files.list(PATH).filter(f -> !f.getFileName().toString().startsWith("."))
      .filter(f -> Files.isDirectory(f) || f.toString().endsWith(".java"))
      .map(PATH::relativize).map(Path::toString).collect(Collectors.toList());
  }

  @NotNull
  private static String getExpectedTest(@NotNull FileData data) {
    ErrorContainer container = data.errorContainer;
    List<ErrorInfo> errors = new ArrayList<>(container.errors());
    STATISTIC.total.add(errors.size());
    errors.removeIf(er ->
                      ContainerUtil.exists(JSpecifyFilteredAnnotationTest.FILTERS,
                                           m -> !m.shouldCount() &&
                                                m.filterActual(data.psiFile, data.stripped, er.lineNumber, er.startLineOffset,
                                                               er.message)));
    STATISTIC.valuable.add(errors.size());
    errors.removeIf(er -> {
      return ContainerUtil.exists(JSpecifyFilteredAnnotationTest.FILTERS,
                                  m -> {
                                    boolean matched = m.shouldCount() &&
                                                      m.filterActual(data.psiFile, data.stripped, er.lineNumber, er.startLineOffset,
                                                                     er.message);
                                    if (matched) {
                                      STATISTIC.skipped.putValue(m.getClass().getName(), new Place(data.path.toString(), er.lineNumber));
                                    }
                                    return matched;
                                  });
    });
    STATISTIC.checked.add(errors.size());
    String restoredText = restoreWithErrors(data.stripped, new ErrorContainer(errors));
    if (JSpecifyFilteredAnnotationTest.FILTERS.isEmpty()) {
      Assert.assertEquals("incorrect restored file", data.fileText, restoredText);
    }
    return restoredText;
  }

  @NotNull
  private static String restoreWithErrors(@NotNull String stripped, @NotNull ErrorContainer container) {
    List<Pair<Integer, String>> indexToText = ContainerUtil.map(container.errors(), error -> Pair.create(
      StringUtil.lineColToOffset(stripped, error.lineNumber, error.startLineOffset), error.message));
    StringBuilder sb = new StringBuilder(stripped);
    int additionalOffset = 0;
    for (Pair<Integer, String> pair : indexToText) {
      String additionalText = pair.second;
      sb.insert(pair.first + additionalOffset, additionalText);
      additionalOffset += additionalText.length();
    }
    return sb.toString();
  }

  @NotNull
  private static ErrorContainer createErrorContainer(@NotNull String text) {
    ErrorContainer container = new ErrorContainer(new ArrayList<>());
    JSPECIFY_PATTERN.matcher(text).results().forEach(m -> {
      String message = m.group();
      int start = m.start();
      LineColumn column = StringUtil.offsetToLineColumn(text, start);
      container.errors.add(new ErrorInfo(column.line, column.column, message));
    });

    return container;
  }

  private interface ErrorFilter {
    boolean filterActual(@NotNull PsiFile file,
                         @NotNull String strippedText,
                         int lineNumber,
                         int startLineOffset,
                         @NotNull String errorMessage);

    boolean filterExpected(@NotNull PsiElement psiElement, @NotNull String errorMessage);

    default boolean shouldCount() {
      return true;
    }

    default void reportUnused() {
    }
  }

  private static class CallWithParameterWithNestedGenericsFilter implements ErrorFilter {

    @Override
    public boolean filterActual(@NotNull PsiFile file,
                                @NotNull String strippedText,
                                int lineNumber,
                                int startLineOffset,
                                @NotNull String errorMessage) {
      if (!errorMessage.contains("jspecify_nullness_mismatch")) return false;
      PsiElement element = findElement(file, strippedText, lineNumber, startLineOffset);
      PsiExpressionStatement statement = PsiTreeUtil.getParentOfType(element, PsiExpressionStatement.class, true);
      if (statement == null) return false;
      PsiExpression expression = statement.getExpression();
      if (!(expression instanceof PsiCallExpression callExpression)) return false;
      PsiMethod method = callExpression.resolveMethod();
      if (method == null) return false;
      return ContainerUtil.exists(method.getParameterList().getParameters(),
                                  parameter -> parameter.getType() instanceof PsiClassType classType && classType.hasParameters());
    }

    @Override
    public boolean filterExpected(@NotNull PsiElement psiElement, @NotNull String errorMessage) {
      //filter only actual file
      return false;
    }
  }

  private static class VariableWithNestedGenericsFilter implements ErrorFilter {

    @Override
    public boolean filterActual(@NotNull PsiFile file,
                                @NotNull String strippedText,
                                int lineNumber,
                                int startLineOffset,
                                @NotNull String errorMessage) {
      if (!errorMessage.contains("jspecify_nullness_mismatch")) return false;
      PsiElement element = findElement(file, strippedText, lineNumber, startLineOffset);
      PsiVariable variable = PsiTreeUtil.getParentOfType(element, PsiVariable.class, true);
      if (variable == null) return false;
      return variable.getType() instanceof PsiClassType classType && classType.hasParameters();
    }

    @Override
    public boolean filterExpected(@NotNull PsiElement psiElement, @NotNull String errorMessage) {
      //filter only actual file
      return false;
    }
  }

  private static class ReturnWithNestedGenericsFilter implements ErrorFilter {

    @Override
    public boolean filterActual(@NotNull PsiFile file,
                                @NotNull String strippedText,
                                int lineNumber,
                                int startLineOffset,
                                @NotNull String errorMessage) {
      if (!errorMessage.contains("jspecify_nullness_mismatch")) return false;
      PsiElement element = findElement(file, strippedText, lineNumber, startLineOffset);
      PsiReturnStatement returnStatement = PsiTreeUtil.getParentOfType(element, PsiReturnStatement.class, true);
      if (returnStatement == null) return false;
      PsiMethod method = PsiTreeUtil.getParentOfType(returnStatement, PsiMethod.class, true);
      return method.getReturnType() instanceof PsiClassType classType && classType.hasParameters();
    }

    @Override
    public boolean filterExpected(@NotNull PsiElement psiElement, @NotNull String errorMessage) {
      //filter only actual file
      return false;
    }
  }


  private static class SkipIndividuallyFilter implements ErrorFilter {
    private final Set<Pair<String, Integer>> places;
    private final Set<Pair<String, Integer>> unusedPlaces;

    private SkipIndividuallyFilter(Set<Pair<String, Integer>> places) {
      this.places = places;
      this.unusedPlaces = new HashSet<>(places);
    }

    @Override
    public boolean filterActual(@NotNull PsiFile file,
                                @NotNull String strippedText,
                                int lineNumber,
                                int startLineOffset,
                                @NotNull String errorMessage) {
      if (!errorMessage.contains("jspecify_nullness_mismatch")) return false;
      return filter(Pair.create(file.getName(), lineNumber));
    }

    @Override
    public boolean filterExpected(@NotNull PsiElement psiElement, @NotNull String errorMessage) {
      if (!errorMessage.contains("jspecify_nullness_mismatch")) return false;
      PsiFile file = psiElement.getContainingFile();
      if (file == null) return false;
      Document document = file.getFileDocument();
      return filter(Pair.create(file.getName(), document.getLineNumber(psiElement.getTextRange().getStartOffset()) - 1));
    }

    private boolean filter(Pair<@NotNull @NlsSafe String, Integer> pair) {
      if (places.contains(pair)) {
        unusedPlaces.remove(pair);
        return true;
      }
      return false;
    }

    @Override
    public void reportUnused() {
      if (unusedPlaces.isEmpty()) return;
      System.out.println("Some filters were unused; probably they are not actual anymore and should be excluded:\n"
                         + StringUtil.join(unusedPlaces, "\n"));
    }
  }

  private static class ReturnSynchronizedWithUnspecifiedFilter implements ErrorFilter {

    @Override
    public boolean shouldCount() {
      return false;
    }

    @Override
    public boolean filterActual(@NotNull PsiFile file,
                                @NotNull String strippedText,
                                int lineNumber,
                                int startLineOffset,
                                @NotNull String errorMessage) {
      if (!errorMessage.contains("jspecify_nullness_mismatch")) return false;
      PsiElement element = findElement(file, strippedText, lineNumber, startLineOffset);
      return filterExpected(element, errorMessage);
    }


    @Override
    public boolean filterExpected(@NotNull PsiElement psiElement, @NotNull String errorMessage) {
      PsiStatement statement = PsiTreeUtil.getParentOfType(psiElement, PsiReturnStatement.class, PsiSynchronizedStatement.class);
      if (statement == null) return false;
      Collection<PsiReferenceExpression> children = PsiTreeUtil.findChildrenOfAnyType(statement, PsiReferenceExpression.class);
      return ContainerUtil.exists(children, child -> {
        PsiElement resolved = child.resolve();
        if (resolved instanceof PsiVariable variable) {
          PsiTypeElement typeElement = variable.getTypeElement();
          return hasUnspecified(typeElement);
        }
        if (resolved instanceof PsiMethod method) {
          return hasUnspecified(method.getReturnTypeElement());
        }
        return false;
      });
    }
  }

  private static boolean hasUnspecified(@Nullable PsiTypeElement element) {
    if (element == null) return false;
    return ContainerUtil.exists(PsiTreeUtil.findChildrenOfType(element, PsiAnnotation.class),
                                a -> a.getText().contains("Unspecified"));
  }

  private static @Nullable PsiElement findElement(@NotNull PsiFile file,
                                                  @NotNull String strippedText,
                                                  int lineNumber,
                                                  int startLineOffset) {
    return file.findElementAt(StringUtil.lineColToOffset(strippedText, lineNumber + 1, startLineOffset) + 1);
  }

  private static class SkipErrorFilter implements ErrorFilter {
    private final String myMessage;


    private SkipErrorFilter(@NotNull String message) {
      myMessage = message;
    }

    @Override
    public boolean shouldCount() {
      return false;
    }

    @Override
    public boolean filterActual(@NotNull PsiFile file,
                                @NotNull String strippedText,
                                int lineNumber,
                                int startLineOffset,
                                @NotNull String errorMessage) {
      return errorMessage.contains(myMessage);
    }

    @Override
    public boolean filterExpected(@NotNull PsiElement psiElement, @NotNull String errorMessage) {
      return errorMessage.contains(myMessage);
    }
  }

  static class JSpecifyNullableStuffInspection extends NullableStuffInspection {
    private final Map<PsiElement, String> warnings;

    JSpecifyNullableStuffInspection(Map<PsiElement, String> warnings) {
      this.warnings = warnings;
    }

    @Override
    protected void reportProblem(@NotNull ProblemsHolder holder,
                                 @NotNull PsiElement anchor,
                                 LocalQuickFix @NotNull [] fixes,
                                 @NotNull String messageKey, Object... args) {
      switch (messageKey) {
        case "inspection.nullable.problems.primitive.type.annotation", "inspection.nullable.problems.receiver.annotation",
             "inspection.nullable.problems.outer.type", "inspection.nullable.problems.at.reference.list",
             "inspection.nullable.problems.at.constructor", "inspection.nullable.problems.at.enum.constant" ->
          warnings.put(anchor, "jspecify_nullness_intrinsically_not_nullable");
        case "inspection.nullable.problems.at.wildcard", "inspection.nullable.problems.at.type.parameter",
             "inspection.nullable.problems.at.local.variable" -> warnings.put(anchor, "jspecify_unrecognized_location");
        case "inspection.nullable.problems.Nullable.method.overrides.NotNull",
             "inspection.nullable.problems.NotNull.parameter.overrides.Nullable",
             "assigning.a.collection.of.nullable.elements"
          //,  "non.null.type.argument.is.expected"  //todo see IDEA-377707
          -> warnings.put(anchor, "jspecify_nullness_mismatch");
        case "inspection.nullable.problems.method.overrides.NotNull", "inspection.nullable.problems.parameter.overrides.NotNull" ->
          warnings.put(anchor, "jspecify_nullness_not_enough_information");
        case "inspection.nullable.problems.Nullable.NotNull.conflict" -> warnings.put(anchor, "jspecify_conflicting_annotations");
      }
    }
  }

  private static class JSpecifyNotNullFieldNotInitializedInspection extends NotNullFieldNotInitializedInspection {
    private final Map<PsiElement, String> warnings;

    JSpecifyNotNullFieldNotInitializedInspection(Map<PsiElement, String> warnings) {
      this.warnings = warnings;
    }

    @Override
    protected void reportProblem(@NotNull ProblemsHolder holder,
                                 PsiElement anchor,
                                 String message,
                                 List<LocalQuickFix> fixes) {
      warnings.put(anchor, "jspecify_nullness_mismatch");
    }
  }

  // Reports dataflow problems in code-analysis-conformant way
  static class JSpecifyDataFlowInspection extends DataFlowInspectionBase {
    private final Map<PsiElement, String> warnings;

    JSpecifyDataFlowInspection(Map<PsiElement, String> warnings) {
      this.warnings = warnings;
    }

    @Override
    protected void reportNullabilityProblems(ProblemReporter reporter,
                                             List<NullabilityProblemKind.NullabilityProblem<?>> problems) {
      for (NullabilityProblemKind.NullabilityProblem<?> problem : problems) {
        String warning = getJSpecifyWarning(problem);
        if (warning != null) {
          warnings.put(problem.getDereferencedExpression(), warning);
        }
      }
    }

    @Override
    protected void reportNullableReturnsProblems(ProblemReporter reporter,
                                                 List<NullabilityProblemKind.NullabilityProblem<?>> problems,
                                                 Nullability nullability,
                                                 PsiAnnotation anno,
                                                 NullableNotNullManager manager) {
      for (NullabilityProblemKind.NullabilityProblem<?> problem : problems) {
        String warning = getJSpecifyWarning(problem);
        if (warning != null) {
          warnings.put(problem.getDereferencedExpression(), warning);
        }
      }
    }

    private static @Nullable String getJSpecifyWarning(NullabilityProblemKind.NullabilityProblem<?> problem) {
      PsiExpression expression = problem.getDereferencedExpression();
      if (expression == null) return null;
      if (problem.getKind() == NullabilityProblemKind.passingToNonAnnotatedParameter) return null;
      if (problem.getKind() == NullabilityProblemKind.nullableReturn) {
        final PsiElement methodOrLambda = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, PsiLambdaExpression.class);
        if (methodOrLambda instanceof PsiMethod method) {
          NullabilityAnnotationInfo info =
            NullableNotNullManager.getInstance(methodOrLambda.getProject()).findEffectiveNullabilityInfo(method);
          if (info == null || info.isInferred()) {
            info = DfaPsiUtil.getTypeNullabilityInfo(PsiTypesUtil.getMethodReturnType(method.getBody()));
          }
          Nullability nullability = info == null ? Nullability.UNKNOWN : info.getNullability();
          if (nullability == Nullability.NULLABLE) return null;
          if (nullability == Nullability.UNKNOWN) return "jspecify_nullness_not_enough_information";
        }
      }
      return problem.hasUnknownNullability() ? "jspecify_nullness_not_enough_information" : "jspecify_nullness_mismatch";
    }
  }

  private record FileData(Path path, String fileText, String stripped, PsiFile psiFile, ErrorContainer errorContainer) {
  }

  private record ErrorContainer(List<ErrorInfo> errors) {
  }

  private record ErrorInfo(int lineNumber, int startLineOffset, String message) {
  }

  private record Statistic(LongAdder total, LongAdder valuable, LongAdder checked, MultiMap<String, Place> skipped) {
    @Override
    public String toString() {
      return "Statistic{" +
             "total=" + total +
             ", valuable=" + valuable +
             ", checked=" + checked +
             ", skipped=\n" + prepareToString(skipped) +
             '}';
    }

    private static String prepareToString(MultiMap<String, Place> map) {
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<String, Collection<Place>> entry : map.entrySet()) {
        sb.append(entry.getKey()).append(" (").append(entry.getValue().size()).append(")").append(": \n");
        for (Place place : entry.getValue()) {
          sb.append("  ").append(place.fileName).append(":").append(place.lineNumber).append("\n");
        }
        sb.append("\n");
      }
      return sb.toString();
    }
  }

  record Place(String fileName, int lineNumber) {
  }
}
