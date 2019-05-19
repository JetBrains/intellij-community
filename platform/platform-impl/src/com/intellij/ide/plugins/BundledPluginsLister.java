// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.google.gson.stream.JsonWriter;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.util.io.FileUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Ivan Chirkov
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class BundledPluginsLister implements ApplicationStarter {
  @Override
  public String getCommandName() {
    return "listBundledPlugins";
  }

  @Override
  public void premain(String[] args) { }

  @Override
  public void main(String[] args) {
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
        IdeaPluginDescriptor[] plugins = PluginManagerCore.getPlugins();

        List<String> modules = Arrays.stream(plugins)
          .flatMap(it -> it instanceof IdeaPluginDescriptorImpl ? ((IdeaPluginDescriptorImpl)it).getModules().stream() : Stream.empty())
          .sorted()
          .collect(Collectors.toList());

        List<String> pluginIds = Arrays.stream(plugins)
                                       .map(plugin -> plugin.getPluginId().getIdString())
                                       .sorted()
                                       .collect(Collectors.toList());

        writer.beginObject();
        writeList(writer, "modules", modules);
        writeList(writer, "plugins", pluginIds);
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