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
package com.intellij.ide.plugins;

import com.google.gson.stream.JsonWriter;
import com.intellij.openapi.application.ApplicationStarterEx;
import com.intellij.openapi.vfs.CharsetToolkit;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Ivan Chirkov
 */
public class BundledPluginsLister extends ApplicationStarterEx {
  @Override
  public boolean isHeadless() {
    return true;
  }

  @Override
  public String getCommandName() {
    return "listBundledPlugins";
  }

  @Override
  public void premain(String[] args) {
  }

  @Override
  public void main(String[] args) {
    try {
      OutputStream out;
      if (args.length == 2) {
        File outFile = new File(args[1]);
        File parentFile = outFile.getParentFile();
        if (parentFile != null) parentFile.mkdirs();
        out = new FileOutputStream(outFile);
      }
      else {
        out = System.out;
      }
      JsonWriter writer = new JsonWriter(new OutputStreamWriter(out, CharsetToolkit.UTF8_CHARSET));

      try {
        IdeaPluginDescriptor[] plugins = PluginManagerCore.getPlugins();

        List<String> modules = Arrays.stream(plugins)
          .filter(IdeaPluginDescriptorImpl.class::isInstance)
          .filter(plugin -> ((IdeaPluginDescriptorImpl)plugin).getModules() != null)
          .flatMap(plugin -> ((IdeaPluginDescriptorImpl)plugin).getModules().stream())
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
      finally {
        writer.close();
      }
    }
    catch (IOException e) {
      e.printStackTrace();
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