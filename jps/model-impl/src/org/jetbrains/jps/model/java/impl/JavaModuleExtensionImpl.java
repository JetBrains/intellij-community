package org.jetbrains.jps.model.java.impl;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsParentElement;
import org.jetbrains.jps.model.impl.JpsElementBase;
import org.jetbrains.jps.model.java.JavaModuleExtension;
import org.jetbrains.jps.model.java.LanguageLevel;

/**
 * @author nik
 */
public class JavaModuleExtensionImpl extends JpsElementBase<JavaModuleExtensionImpl> implements JavaModuleExtension {
  private String myOutputUrl;
  private String myTestOutputUrl;
  private LanguageLevel myLanguageLevel;

  public JavaModuleExtensionImpl(JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    super(eventDispatcher, parent);
  }

  public JavaModuleExtensionImpl(JavaModuleExtensionImpl original, JpsEventDispatcher dispatcher, JpsParentElement parent) {
    super(original, dispatcher, parent);
    myOutputUrl = original.myOutputUrl;
    myTestOutputUrl = original.myTestOutputUrl;
    myLanguageLevel = original.myLanguageLevel;
  }

  @NotNull
  @Override
  public JavaModuleExtensionImpl createCopy(@NotNull JpsModel model, @NotNull JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    return new JavaModuleExtensionImpl(this, eventDispatcher, parent);
  }

  @Override
  public String getOutputUrl() {
    return myOutputUrl;
  }

  @Override
  public void setOutputUrl(String outputUrl) {
    if (!Comparing.equal(myOutputUrl, outputUrl)) {
      myOutputUrl = outputUrl;
      getEventDispatcher().fireElementChanged(this);
    }
  }

  @Override
  public String getTestOutputUrl() {
    return myTestOutputUrl;
  }

  @Override
  public void setTestOutputUrl(String testOutputUrl) {
    if (!Comparing.equal(myTestOutputUrl, testOutputUrl)) {
      myTestOutputUrl = testOutputUrl;
      getEventDispatcher().fireElementChanged(this);
    }
  }

  @Override
  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  @Override
  public void setLanguageLevel(LanguageLevel languageLevel) {
    if (!Comparing.equal(myLanguageLevel, languageLevel)) {
      myLanguageLevel = languageLevel;
      getEventDispatcher().fireElementChanged(this);
    }
  }

  public void applyChanges(@NotNull JavaModuleExtensionImpl modified) {
    setLanguageLevel(modified.myLanguageLevel);
    setOutputUrl(modified.myOutputUrl);
    setTestOutputUrl(modified.myTestOutputUrl);
  }
}
