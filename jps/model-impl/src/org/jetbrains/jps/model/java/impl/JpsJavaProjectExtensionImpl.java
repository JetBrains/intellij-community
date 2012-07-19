package org.jetbrains.jps.model.java.impl;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.impl.JpsElementBase;
import org.jetbrains.jps.model.java.JpsJavaProjectExtension;
import org.jetbrains.jps.model.java.LanguageLevel;

/**
 * @author nik
 */
public class JpsJavaProjectExtensionImpl extends JpsElementBase<JpsJavaProjectExtensionImpl> implements JpsJavaProjectExtension {
  private String myOutputUrl;
  private LanguageLevel myLanguageLevel;

  public JpsJavaProjectExtensionImpl() {
  }

  private JpsJavaProjectExtensionImpl(JpsJavaProjectExtensionImpl original) {
    myOutputUrl = original.myOutputUrl;
    myLanguageLevel = original.myLanguageLevel;
  }

  @NotNull
  @Override
  public JpsJavaProjectExtensionImpl createCopy() {
    return new JpsJavaProjectExtensionImpl(this);
  }

  public String getOutputUrl() {
    return myOutputUrl;
  }

  public void setOutputUrl(String outputUrl) {
    if (!Comparing.equal(myOutputUrl, outputUrl)) {
      myOutputUrl = outputUrl;
      fireElementChanged();
    }
  }

  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  public void setLanguageLevel(LanguageLevel languageLevel) {
    if (!Comparing.equal(myLanguageLevel, languageLevel)) {
      myLanguageLevel = languageLevel;
      fireElementChanged();
    }
  }

  @Override
  public void applyChanges(@NotNull JpsJavaProjectExtensionImpl modified) {
    setLanguageLevel(modified.myLanguageLevel);
    setOutputUrl(modified.myOutputUrl);
  }
}
