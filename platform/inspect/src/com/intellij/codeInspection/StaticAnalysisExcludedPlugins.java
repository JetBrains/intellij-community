// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.google.gson.stream.JsonWriter;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationStarter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@NonNls
final class StaticAnalysisExcludedPlugins implements ApplicationStarter {
  List<String> pluginsToInclude = Arrays.asList(
    "java",
    "java-ide-customization",
    "Hibernate",
    "android",
    "smali",
    "ant",
    "BeanValidation",
    "CDI",
    "uiDesigner",
    "StrutsAssistant",
    "JavaEE",
    "CSS",
    "FreeMarker",
    "devkit",
    "htmltools",
    "maven",
    "aop-common",
    "w3validators",
    "sql",
    "StrutsAssistant",
    "GwtStudio",
    "Seam",
    "junit",
    "WebServices",
    "IntelliLang",
    "jsp",
    "xpath",
    "JSF",
    "weblogicIntegration",
    "Spring",
    "SpringBatch",
    "SpringData",
    "SpringIntegration",
    "SpringMVC",
    "SpringSecurity",
    "SpringWebflow",
    "SpringWebServices",
    "eclipse",
    "testng",
    "struts2",
    "Velocity",
    "Guice",
    "Uml",
    "PersistenceSupport",
    "ToString",
    "DatabaseTools",
    "properties",
    "BeanValidation",
    "java-i18n",
    "SpellChecker",
    "Groovy",
    "structuralsearch",
    "gradle",
    "Kotlin"
  );
  @Override
  public String getCommandName() {
    return "saExcluded";
  }

  @Override
  public int getRequiredModality() {
    return NOT_IN_EDT;
  }

  @Override
  public void main(@NotNull List<String> args) {
    try {
      Writer out;
      if (args.size() == 2) {
        Path outFile = Paths.get(args.get(1));
        Files.createDirectories(outFile.getParent());
        out = Files.newBufferedWriter(outFile);
      }
      else {
        // noinspection UseOfSystemOutOrSystemErr,IOResourceOpenedButNotSafelyClosed
        out = new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
      }

      List<? extends IdeaPluginDescriptor> plugins = PluginManagerCore.getLoadedPlugins();
      List<String> pluginIds = new ArrayList<>(plugins.size());
      List<String> toExclude = new ArrayList<>(plugins.size());
      for (IdeaPluginDescriptor plugin : plugins) {
        Path path = plugin.getPluginPath();
        String pathName = path.getName(path.getNameCount() - 1).getFileName().toString();
        String id = plugin.getPluginId().getIdString();
        pluginIds.add(id + "; " + pathName + "; " + path + "\n");
        if (!pluginsToInclude.contains(pathName)) {
          toExclude.add(id + "\n");
        }
      }

      pluginIds.sort(null);
      toExclude.sort(null);

      try (out) {
        pluginIds.forEach(it -> {
          try {
            out.write(it, 0, it.length() );
          }
          catch (IOException e) {
            //noinspection UseOfSystemOutOrSystemErr
            e.printStackTrace(System.err);
            System.exit(1);
          }
        });

        out.write("\n To exclude: \n \n");

        toExclude.forEach(it -> {
          try {
            out.write(it, 0, it.length() );
          }
          catch (IOException e) {
            //noinspection UseOfSystemOutOrSystemErr
            e.printStackTrace(System.err);
            System.exit(1);
          }
        });
      }

    }
    catch (IOException e) {
      //noinspection UseOfSystemOutOrSystemErr
      e.printStackTrace(System.err);
      System.exit(1);
    }

    System.exit(0);
  }

  private static void writeList(@NotNull JsonWriter writer, String name, @NotNull List<String> elements) throws IOException {
    writer.name(name).beginArray();
    for (String module : elements) {
      writer.value(module);
    }
    writer.endArray();
  }
}