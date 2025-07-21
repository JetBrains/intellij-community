// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The GenerateJavaFormattingStyleCollector class is responsible for generating a JavaFormattingStyleCollector file
 * In general, it is better to avoid auto-generated collectors, but there is a problem
 * that the settings are fields, and we still would need manual reporting.
 * Another problem is the number of these fields.
 * After simplifying Java code style settings, it is better to write normal collector
 */
@SuppressWarnings("NewClassNamingConvention")
@Ignore
public class GenerateJavaFormattingStyleCollector extends TestCase {

  public static final String PATH =
    "/community/java/java-impl/src/com/intellij/internal/statistic/JavaFormattingStyleCollector.kt";

  /**
   * Generates JavaFormattingStyleCollector.
   * Please increase the version number and run this test.
   * After that, check file com.intellij.internal.statistic.JavaFormattingStyleCollector
   */
  public void testGenerate() {
    int version = 3; //change it
    List<String> names2 = new ArrayList<>();
    List<String> collectors4 = new ArrayList<>();
    collectFrom(CommonCodeStyleSettings.class, names2, collectors4, "commonSettings", "defaultCommonSettings", "COMMON_");
    collectFrom(JavaCodeStyleSettings.class, names2, collectors4, "javaSettings", "defaultJavaSettings", "JAVA_");
    String newFile = generateFile(names2, collectors4, version);
    writeFile(newFile);
  }

  private static void writeFile(String text) {
    try {
      String homePath = PathManager.getHomePath();
      File file = new File(homePath + PATH);
      FileUtil.writeToFile(file, text);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  private static String generateFile(@NotNull List<String> names2, @NotNull List<String> collectors4, int version) {
    String events2String = prepareLines(names2, 2, ",\n");
    String collectors4String = prepareLines(collectors4, 4, "\n");
    return TEMPLATE_FILE
      .replace("<version>", String.valueOf(version))
      .replace("<names2>", events2String)
      .replace("<collectors4>", collectors4String);
  }

  @NotNull
  private static String prepareLines(@NotNull List<String> lines, int indentNumber, String separator) {
    String indents = " ".repeat(indentNumber);
    return lines.stream().map(line -> indents + line)
      .collect(Collectors.joining(separator));
  }

  private static void collectFrom(@NotNull Class<?> settingsClass,
                                  @NotNull List<String> names6,
                                  @NotNull List<String> collectors4,
                                  @NotNull String nameSetting,
                                  @NotNull String nameDefaultSetting,
                                  @NotNull String prefix) {
    for (Field field : settingsClass.getDeclaredFields()) {
      if (!(Modifier.isPublic(field.getModifiers()) &&
            !Modifier.isStatic(field.getModifiers()) &&
            !Modifier.isFinal(field.getModifiers()))) {
        continue;
      }
      if (!(field.getType().equals(boolean.class) || (field.getType().equals(int.class)))) {
        continue;
      }

      Deprecated annotation = field.getAnnotation(Deprecated.class);
      if (annotation != null) {
        continue;
      }
      String fieldName = field.getName();
      String name = prefix + fieldName;
      names6.add("\"" + name + "\"");
      collectors4.add(
        "addMetricIfDiffersCustom(result, {nameSetting}, {nameDefaultSetting}, { s -> s.{fieldName} }, \"{name}\")"
          .replace("{name}", name)
          .replace("{fieldName}", fieldName)
          .replace("{nameSetting}", nameSetting)
          .replace("{nameDefaultSetting}", nameDefaultSetting)
      );
    }
  }

  @Language("kotlin")
  private static final String TEMPLATE_FILE =
    """
      // Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
      package com.intellij.internal.statistic
      
      import com.intellij.application.options.CodeStyle
      import com.intellij.internal.statistic.beans.MetricEvent
      import com.intellij.internal.statistic.beans.addMetricIfDiffers
      import com.intellij.internal.statistic.eventLog.EventLogGroup
      import com.intellij.internal.statistic.eventLog.events.EventFields
      import com.intellij.internal.statistic.eventLog.events.StringEventField
      import com.intellij.internal.statistic.eventLog.events.VarargEventId
      import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
      import com.intellij.lang.java.JavaLanguage
      import com.intellij.openapi.project.Project
      import com.intellij.psi.codeStyle.CommonCodeStyleSettings
      import com.intellij.psi.codeStyle.JavaCodeStyleSettings
      
      /**
       * PLEASE DON'T EDIT MANUALLY,
       * USE com.intellij.psi.codeStyle.GenerateJavaFormattingStyleCollector
       */
      public class JavaFormattingStyleCollector : ProjectUsagesCollector() {
        private val GROUP = EventLogGroup("java.code.style", <version>)
      
        override fun getGroup(): EventLogGroup {
          return GROUP
        }
      
        private val NAME_FIELD = EventFields.String("name", ALLOWED_NAMES)
      
        private val VALUE_FIELD = object : StringEventField("value") {
          override val validationRule: List<String>
            get() = listOf("{regexp#integer}", "{enum#boolean}")
        }
  
        private val NOT_DEFAULT_EVENT: VarargEventId = GROUP.registerVarargEvent("not.default", NAME_FIELD, VALUE_FIELD)
      
        private inline fun <T, V> addMetricIfDiffersCustom(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                                                           crossinline valueFunction: (T) -> V, key: String) {
          addMetricIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction) { value ->
            NOT_DEFAULT_EVENT.metric(NAME_FIELD.with(key), VALUE_FIELD.with(value.toString()))
          }
        }
      
        override fun getMetrics(project: Project): Set<MetricEvent> {
          val result = mutableSetOf<MetricEvent>()
          val commonSettings = CodeStyle.getSettings(project).getCommonSettings(JavaLanguage.INSTANCE)
          val defaultCommonSettings = CommonCodeStyleSettings(JavaLanguage.INSTANCE)
      
          val javaSettings = CodeStyle.getSettings(project).getCustomSettings(JavaCodeStyleSettings::class.java)
          val defaultJavaSettings = JavaCodeStyleSettings(javaSettings.container)
      
      <collectors4>
          return result
        }
      }
      
      private val ALLOWED_NAMES = listOf(
      <names2>
      )""";
}
