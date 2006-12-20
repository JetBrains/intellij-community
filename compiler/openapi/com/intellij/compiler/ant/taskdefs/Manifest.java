package com.intellij.compiler.ant.taskdefs;

import com.intellij.compiler.ant.Tag;
import com.intellij.openapi.compiler.make.ManifestBuilder;
import com.intellij.openapi.util.Pair;

import java.util.jar.Attributes;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class Manifest extends Tag{
  public Manifest() {
    super("manifest", new Pair[] {});
  }

  public void applyAttributes(final java.util.jar.Manifest manifest) {
    ManifestBuilder.setGlobalAttributes(manifest.getMainAttributes());
    final Attributes mainAttributes = manifest.getMainAttributes();
    for (final Object o : mainAttributes.keySet()) {
      Attributes.Name name = (Attributes.Name)o;
      String value = (String)mainAttributes.get(name);
      add(new Attribute(name.toString(), value));
    }
  }
}
