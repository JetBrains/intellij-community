// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.google.gson.stream.JsonWriter;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextLikeFileType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

final class BundledPluginsLister implements ApplicationStarter {
  @Override
  public String getCommandName() {
    return "listBundledPlugins";
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

      try (JsonWriter writer = new JsonWriter(out)) {
        List<? extends IdeaPluginDescriptor> plugins = PluginManagerCore.getLoadedPlugins();
        List<String> modules = new ArrayList<>();
        List<String> pluginIds = new ArrayList<>(plugins.size());
        for (IdeaPluginDescriptor plugin : plugins) {
          pluginIds.add(plugin.getPluginId().getIdString());

          for (PluginId pluginId : ((IdeaPluginDescriptorImpl)plugin).getModules()) {
            modules.add(pluginId.getIdString());
          }
        }

        pluginIds.sort(null);
        modules.sort(null);

        FileTypeManager fileTypeManager = FileTypeManager.getInstance();
        List<String> extensions = new ArrayList<>();
        for (FileType type : fileTypeManager.getRegisteredFileTypes()) {
          if (!(type instanceof PlainTextLikeFileType)) {
            for (FileNameMatcher matcher : fileTypeManager.getAssociations(type)) {
              extensions.add(matcher.getPresentableString());
            }
          }
        }

        writer.beginObject();
        writeList(writer, "modules", modules);
        writeList(writer, "plugins", pluginIds);
        writeList(writer, "extensions", extensions);
        writer.endObject();
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