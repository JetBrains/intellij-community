package com.intellij.openapi.keymap;

import com.intellij.openapi.keymap.impl.BundledKeymapProvider;

import java.util.Arrays;
import java.util.List;

/**
 * @author yole
 */
public class JBuilderKeymapProvider implements BundledKeymapProvider {
  public List<String> getKeymapFileNames() {
    return Arrays.asList("JBuilderKeymap.xml");
  }
}
