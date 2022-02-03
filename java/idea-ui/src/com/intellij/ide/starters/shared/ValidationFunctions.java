// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters.shared;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.starters.JavaStartersBundle;
import com.intellij.openapi.observable.properties.GraphProperty;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.impl.PsiNameHelperImpl;
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public final class ValidationFunctions {
  public static final TextValidationFunction CHECK_PACKAGE_NAME = (fieldText) -> {
    if (!PsiNameHelperImpl.getInstance().isQualifiedName(fieldText)) {
      return JavaStartersBundle.message("message.some.string.is.not.a.valid.package.name", fieldText);
    }
    return null;
  };

  public static final TextValidationFunction CHECK_SIMPLE_NAME_FORMAT = new TextValidationFunction() {
    private final Pattern myPattern = Pattern.compile("[a-zA-Z0-9-._ ]*"); // IDEA-235441

    @Override
    public String checkText(String fieldText) {
      if (!myPattern.matcher(fieldText).matches()) {
        return JavaStartersBundle.message(
          "message.only.latin.characters.digits.spaces.and.some.other.symbols.are.allowed.here");
      }
      return null;
    }
  };

  public static final TextValidationFunction CHECK_NOT_EMPTY = (fieldText) -> {
    if (fieldText.isEmpty()) {
      return JavaStartersBundle.message("message.field.must.be.set");
    }
    return null;
  };

  public static final TextValidationFunction CHECK_NO_WHITESPACES = (fieldText) -> {
    if (fieldText.contains(" ")) {
      return JavaStartersBundle.message("message.whitespaces.are.not.allowed.here");
    }
    return null;
  };

  // IDEA-235887 prohibit using some words reserved by Windows in group and artifact fields
  public static final TextValidationFunction CHECK_NO_RESERVED_WORDS =
    new TextValidationFunction() {
      private final Pattern myPattern = Pattern.compile("(^|[ .])(con|prn|aux|nul|com\\d|lpt\\d)($|[ .])",
                                                        Pattern.CASE_INSENSITIVE);

      @Override
      public String checkText(String fieldText) {
        if (myPattern.matcher(fieldText).find()) {
          return JavaStartersBundle.message("message.some.parts.are.not.allowed.here");
        }
        return null;
      }
    };

  // This validation describes the most common and important rules for all Web Starters implementations
  public static final TextValidationFunction CHECK_GROUP_FORMAT = new TextValidationFunction() {
    private final Pattern myPatternForEntireText = Pattern.compile("[a-zA-Z\\d_.-]*");
    private final Pattern myPatternForOneWord = Pattern.compile("[a-zA-Z_].*");

    @Override
    public String checkText(String fieldText) {
      if (!myPatternForEntireText.matcher(fieldText).matches()) {
        return JavaStartersBundle.message("message.only.latin.characters.digits.and.some.other.symbols.are.allowed.here");
      }

      char firstSymbol = fieldText.charAt(0);
      char lastSymbol = fieldText.charAt(fieldText.length() - 1);
      if (firstSymbol == '.' || lastSymbol == '.') {
        return JavaStartersBundle.message("message.must.not.start.or.end.with.dot");
      }

      if (fieldText.contains("..")) {
        return JavaStartersBundle.message("message.must.not.contain.double.dot.sequences");
      }

      String[] wordsBetweenDots = fieldText.split("\\.");
      for (String word : wordsBetweenDots) {
        if (!myPatternForOneWord.matcher(word).matches()) {
          return JavaStartersBundle.message("message.part.is.incorrect.and.must.start.with.latin.character.or.some.other.symbols", word);
        }
      }

      return null;
    }
  };

  public static final TextValidationFunction CHECK_ARTIFACT_SIMPLE_FORMAT = new TextValidationFunction() {
    private final Pattern myUsedSymbolsCheckPattern = Pattern.compile("[a-zA-Z0-9-_]*");
    private final Pattern myFirstSymbolCheckPattern = Pattern.compile("[a-zA-Z_].*");

    @Override
    public String checkText(String fieldText) {
      if (!myUsedSymbolsCheckPattern.matcher(fieldText).matches()) {
        return JavaStartersBundle.message("message.allowed.symbols.for.check.artifact.simple.format");
      }
      if (!myFirstSymbolCheckPattern.matcher(fieldText).matches()) {
        return JavaStartersBundle.message("message.allows.first.symbol.for.check.artifact.simple.format");
      }
      return null;
    }
  };

  /**
   * @deprecated Use {@link #createLocationWarningValidator(GraphProperty)} with changes of IDEA-283336
   */
  @ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated
  public static final TextValidationFunction CHECK_LOCATION_FOR_WARNING = fieldText -> {
    File file = Paths.get(FileUtil.expandUserHome(fieldText)).toFile();
    if (file.exists()) {
      String[] children = file.list();
      if (children != null && children.length > 0) {
        return JavaStartersBundle.message("message.directory.not.empty.warning");
      }
    }
    return null;
  };

  /**
   * Validates Name property using additional location field value, checks if the resulting directory does not exist or empty.
   */
  public static TextValidationFunction createLocationWarningValidator(GraphProperty<String> locationProperty) {
    return fieldText -> {
      File file = Paths.get(FileUtil.expandUserHome(FileUtil.join(locationProperty.get(), fieldText))).toFile();
      if (file.exists()) {
        String[] children = file.list();
        if (children != null && children.length > 0) {
          return JavaStartersBundle.message("message.directory.0.not.empty.warning", file.getAbsolutePath());
        }
      }
      return null;
    };
  }

  public static final TextValidationFunction CHECK_LOCATION_FOR_ERROR = fieldText -> {
    Path locationPath;
    try {
      locationPath = Paths.get(fieldText);
    } catch (InvalidPathException e) {
      return JavaStartersBundle.message("message.specified.path.is.illegal");
    }

    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      if (ProjectUtil.isSameProject(locationPath, project)) {
        return JavaStartersBundle.message("message.directory.already.taken.error", project.getName());
      }
    }

    File file = locationPath.toFile();
    if (file.exists()) {
      if (!file.canWrite()) {
        return JavaStartersBundle.message("message.directory.not.writable.error");
      }
      String[] children = file.list();
      if (children == null) {
        return JavaStartersBundle.message("message.file.not.directory.error");
      }
    }

    return null;
  };

  // This validation describes the most common and important rules for all Web Starters implementations
  public static final TextValidationFunction CHECK_ARTIFACT_FORMAT_FOR_WEB = new TextValidationFunction() {
    private final Pattern myPattern = Pattern.compile("[a-z0-9-._]*");

    @Override
    public String checkText(String fieldText) {
      if (!myPattern.matcher(fieldText).matches()) {
        return JavaStartersBundle.message("message.only.lowercase.latin.characters.digits.and.some.other.symbols.are.allowed.here");
      }
      if (fieldText.charAt(0) < 'a' || fieldText.charAt(0) > 'z') {
        return JavaStartersBundle.message("message.must.start.with.lowercase.latin.character");
      }
      return null;
    }
  };
}
