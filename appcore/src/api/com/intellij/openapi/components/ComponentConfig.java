package com.intellij.openapi.components;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;

import java.util.HashMap;
import java.util.Map;

@Tag("component")
public class ComponentConfig {
  public static final ComponentConfig[] EMPTY_ARRAY = new ComponentConfig[0];
  @Tag("implementation-class")
  public String implementationClass;

  @Tag("interface-class")
  public String interfaceClass;

  @Tag("headless-implementation-class")
  public String headlessImplementationClass;

  //todo: empty tag means TRUE
  @Tag(value = "skipForDummyProject", textIfEmpty="true")
  public boolean skipForDummyProject;

  @Property(surroundWithTag = false)
  @MapAnnotation(surroundWithTag = false, entryTagName = "option", keyAttributeName = "name", valueAttributeName = "value")
  public Map<String,String> options = new HashMap<String, String>();

  @Transient
  public IdeaPluginDescriptor pluginDescriptor;

  @Transient
  public ClassLoader getClassLoader() {
    return pluginDescriptor != null ? pluginDescriptor.getPluginClassLoader() : getClass().getClassLoader();
  }
}
