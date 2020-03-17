// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.google.gson.stream.JsonWriter;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextLikeFileType;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Ivan Chirkov
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
final class BundledPluginsLister implements ApplicationStarter {
  @Override
  public String getCommandName() {
    return "listBundledPlugins";
  }

  @Override
  public void main(@NotNull String[] args) {
    try {
      OutputStream out;
      if (args.length == 2) {
        File outFile = new File(args[1]);
        FileUtil.createParentDirs(outFile);
        //noinspection IOResourceOpenedButNotSafelyClosed
        out = new FileOutputStream(outFile);
      }
      else {
        out = System.out;
      }

      try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
        List<? extends IdeaPluginDescriptor> plugins = PluginManagerCore.getLoadedPlugins();
        List<String> modules = new ArrayList<>();
        for (IdeaPluginDescriptor it : plugins) {
          if (it instanceof IdeaPluginDescriptorImpl) {
            for (PluginId pluginId : ((IdeaPluginDescriptorImpl)it).getModules()) {
              modules.add(pluginId.getIdString());
            }
          }
        }
        modules.sort(null);

        List<String> pluginIds = plugins.stream()
          .map(plugin -> plugin.getPluginId().getIdString())
          .sorted()
          .collect(Collectors.toList());

        FileTypeManager fileTypeManager = FileTypeManager.getInstance();
        List<String> extensions = Arrays.stream(fileTypeManager.getRegisteredFileTypes()).
          filter(type -> !(type instanceof PlainTextLikeFileType)).
          flatMap(type -> fileTypeManager.getAssociations(type).stream()).
          map(matcher -> matcher.getPresentableString()).
          collect(Collectors.toList());

        writer.beginObject();
        writeList(writer, "modules", modules);
        writeList(writer, "plugins", pluginIds);
        writeList(writer, "extensions", extensions);
        writer.endObject();
      }
    }
    catch (IOException e) {
      e.printStackTrace(System.err);
      System.exit(1);
    }

    System.exit(0);
  }

  private static void writeList(JsonWriter writer, String name, List<String> elements) throws IOException {
    writer.name(name).beginArray();
    for (String module : elements) {
      writer.value(module);
    }
    writer.endArray();
  }
}