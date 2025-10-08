// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.nullable.NullableStuffInspection;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.LineColumn;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.java.PsiAnnotationImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.intellij.psi.impl.source.tree.java.PsiAnnotationImpl.getAnnotationShortName;

@RunWith(Parameterized.class)
public class JSpecifyConformanceAnnotationTest extends LightJavaCodeInsightFixtureTestCase {
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

  private static final String PACKAGE_NAME = "org.jspecify.annotations";
  private static final Path PATH = Paths.get(JavaTestUtil.getJavaTestDataPath(), "/inspection/dataFlow/jspecifyConformance/");
  @Parameterized.Parameter
  public String myFileName;

  @Parameterized.Parameters(name = "{0}")
  public static List<String> getData() throws IOException {
    List<String> files = Files.walk(PATH)
      .filter(f -> !f.getFileName().toString().startsWith("."))
      .filter(f -> f.toString().endsWith(".java"))
      .filter(p -> Files.isRegularFile(p))
      .map(PATH::relativize).map(Path::toString)
      .toList();

    return files;
  }

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

  private static boolean suppressWarning(@NotNull String message, String fileName, Integer offset) {
    Set<Pair<String, Integer>> suppressed = Set.of(
      // These two exceptions are expected: unlike JSpecify, we don't assign NotNull nullness to non-final catch parameter
      // Instead, we are doing the flow analysis and may change the nullness during the variable lifetime
      // See IDEA-377763 for details
      Pair.create("Other.java", 72),
      Pair.create("Other.java", 70)
    );
    LineColumn column = StringUtil.offsetToLineColumn(message, offset);
    return suppressed.contains(Pair.create(fileName, column.line));
  }

  private void mockAnnotations() {
    String template = "package " + PACKAGE_NAME + ";import java.lang.annotation.*;\n\n@Target(%s)public @interface %s {}";
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE_USE", "NonNull"));
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE_USE", "Nullable"));
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE_USE", "NullnessUnspecified"));
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE, ElementType.PACKAGE", "NullMarked"));
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE, ElementType.PACKAGE", "NullUnmarked"));
    myFixture.addClass("""
                         package org.jspecify.conformance.deps.nullmarked;
                         import org.jspecify.annotations.*;
                         @NullMarked
                         public interface NHolder<N extends @Nullable Object> {}
                         """);
  }

  @Test
  public void test() throws IOException {
    Path path = PATH.resolve(myFileName);
    String fileText = Files.readString(path).replace("\r\n", "\n");
    fileText = clean(fileText, path.getFileName().toString());
    String stripped = stripErrors(fileText, offset -> true);
    String relativeFile = FileUtil.toSystemIndependentName((myFileName));
    PsiFile file = myFixture.addFileToProject(relativeFile, stripped);

    Map<PsiElement, String> actual = new LinkedHashMap<>();
    var dfaInspection = new JSpecifyFilteredAnnotationTest.JSpecifyDataFlowInspection(actual);
    dfaInspection.TREAT_UNKNOWN_MEMBERS_AS_NULLABLE = true;
    var nullableStuffInspection = new JSpecifyNullableStuffInspection(actual);
    List<LocalInspectionTool> inspections = List.of(dfaInspection, nullableStuffInspection);
    ReadAction.run(() -> {
      ProblemsHolder holder = new ProblemsHolder(new InspectionManagerEx(getProject()), file, false);
      for (LocalInspectionTool inspection : inspections) {
        PsiElementVisitor visitor = inspection.buildVisitor(holder, false);
        PsiTreeUtil.processElements(file, e -> {
          e.accept(visitor);
          return true;
        });
      }
    });
    String actualText = getActualText(actual, stripped);
    assertEquals("Messages don't match (" + path.getFileName().toString() + ")", fileText, actualText);
  }

  String getActualText(Map<PsiElement, String> actual, String stripped) {
    StringBuilder sb = new StringBuilder();
    int pos = 0;
    TreeMap<Integer, List<String>> map = EntryStream.of(actual)
      .mapKeys(e -> e.getTextRange().getStartOffset())
      .grouping(TreeMap::new, Collectors.toList());
    for (String str : stripped.split("\n", -1)) {
      int endPos = pos + str.length() + 1;
      String warnings = StreamEx.of(map.subMap(pos, endPos).values()).flatMap(List::stream).distinct().joining(" & ");
      //fix order
      if (warnings.equals("test:irrelevant-annotation:NonNull & test:irrelevant-annotation:Nullable")) {
        warnings = "test:irrelevant-annotation:Nullable & test:irrelevant-annotation:NonNull";
      }
      if (!warnings.isEmpty()) {
        sb.append("// ").append(warnings);
      }
      if (!sb.isEmpty()) sb.append("\n");
      pos = endPos;
      sb.append(str);
    }
    return sb.toString();
  }

  private static final Pattern CANNOT_CONVERT = Pattern.compile("// test:cannot-convert.*\n");
  private static final Pattern JOIN_TEST_IRRELEVANT_ANNOTATIONS =
    Pattern.compile("// test:irrelevant[^\\r\\n]*\n *// test:irrelevant.*\n");

  private static @NotNull String clean(@NotNull String text, String fileName) {
    text = JOIN_TEST_IRRELEVANT_ANNOTATIONS.matcher(text)
      .replaceAll("// test:irrelevant-annotation:Nullable & test:irrelevant-annotation:NonNull\n");
    text = CANNOT_CONVERT.matcher(text).replaceAll("// jspecify_nullness_mismatch\n");
    @NotNull String finalText = text;
    text = stripErrors(text, offset -> suppressWarning(finalText, fileName, offset));
    return text;
  }


  private static final Pattern JSPECIFY_TEST = Pattern.compile("(// test:.*\n|// jspecify_.*\n)");

  private static @NotNull String stripErrors(@NotNull String text, Predicate<Integer> deleteWarning) {
    List<TextRange> errors = new ArrayList<>();
    JSPECIFY_TEST.matcher(text).results().forEach(r -> {
      String message = r.group();
      if (message.contains("test:name") ||
          message.contains("test:expression-type") ||
          message.contains("test:sink-type")) {
        return;
      }
      else if (message.contains("test:irrelevant-annotation") ||
               message.contains("test:cannot-convert") ||
               message.contains("jspecify_")) {
        errors.add(new TextRange(r.start(), r.end() - 1));
        return;
      }
      throw new UnsupportedOperationException("Unknown test: " + message);
    });
    if (!errors.isEmpty()) {
      Collections.sort(errors, Comparator.comparingInt(TextRange::getStartOffset).reversed());
      StringBuilder sb = new StringBuilder(text);
      for (TextRange error : errors) {
        if (!deleteWarning.test(error.getStartOffset())) continue;
        sb.replace(error.getStartOffset(), error.getEndOffset(), "");
      }
      return sb.toString();
    }
    return text;
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
        case "inspection.nullable.problems.primitive.type.annotation",
             "inspection.nullable.problems.at.throws",
             "inspection.nullable.problems.at.type.parameter",
             "inspection.nullable.problems.Nullable.NotNull.conflict",
             "conflicting.nullability.annotations",
             "inspection.nullable.problems.at.wildcard",
             "inspection.nullable.problems.at.local.variable" ->
          warnings.put(anchor, "test:irrelevant-annotation:" + getAnnotationShortName(((PsiAnnotationImpl)anchor).getQualifiedName()));
      }
    }
  }
}
