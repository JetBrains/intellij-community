package org.jetbrains.jps.model.java.impl;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsUrlList;
import org.jetbrains.jps.model.impl.JpsCompositeElementBase;
import org.jetbrains.jps.model.impl.JpsUrlListKind;
import org.jetbrains.jps.model.java.JpsJavaModuleExtension;
import org.jetbrains.jps.model.java.LanguageLevel;

/**
 * @author nik
 */
public class JpsJavaModuleExtensionImpl extends JpsCompositeElementBase<JpsJavaModuleExtensionImpl> implements JpsJavaModuleExtension {
  private static final JpsUrlListKind JAVADOC_ROOTS_KIND = new JpsUrlListKind("javadoc roots");
  private static final JpsUrlListKind ANNOTATIONS_ROOTS_KIND = new JpsUrlListKind("annotation roots");
  private String myOutputUrl;
  private String myTestOutputUrl;
  private boolean myInheritOutput;
  private boolean myExcludeOutput;
  private LanguageLevel myLanguageLevel;

  public JpsJavaModuleExtensionImpl() {
    myContainer.setChild(JAVADOC_ROOTS_KIND);
    myContainer.setChild(ANNOTATIONS_ROOTS_KIND);
  }

  private JpsJavaModuleExtensionImpl(JpsJavaModuleExtensionImpl original) {
    super(original);
    myOutputUrl = original.myOutputUrl;
    myTestOutputUrl = original.myTestOutputUrl;
    myLanguageLevel = original.myLanguageLevel;
  }

  @NotNull
  @Override
  public JpsJavaModuleExtensionImpl createCopy() {
    return new JpsJavaModuleExtensionImpl(this);
  }

  @Override
  public JpsUrlList getAnnotationRoots() {
    return myContainer.getChild(ANNOTATIONS_ROOTS_KIND);
  }

  @Override
  public JpsUrlList getJavadocRoots() {
    return myContainer.getChild(JAVADOC_ROOTS_KIND);
  }

  @Override
  public String getOutputUrl() {
    return myOutputUrl;
  }

  @Override
  public void setOutputUrl(String outputUrl) {
    if (!Comparing.equal(myOutputUrl, outputUrl)) {
      myOutputUrl = outputUrl;
      fireElementChanged();
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
      fireElementChanged();
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
      fireElementChanged();
    }
  }

  public void applyChanges(@NotNull JpsJavaModuleExtensionImpl modified) {
    setLanguageLevel(modified.myLanguageLevel);
    setInheritOutput(modified.myInheritOutput);
    setExcludeOutput(modified.myExcludeOutput);
    setOutputUrl(modified.myOutputUrl);
    setTestOutputUrl(modified.myTestOutputUrl);
  }

  @Override
  public boolean isInheritOutput() {
    return myInheritOutput;
  }

  @Override
  public void setInheritOutput(boolean inheritOutput) {
    if (myInheritOutput != inheritOutput) {
      myInheritOutput = inheritOutput;
      fireElementChanged();
    }
  }

  @Override
  public boolean isExcludeOutput() {
    return myExcludeOutput;
  }

  @Override
  public void setExcludeOutput(boolean excludeOutput) {
    if (myExcludeOutput != excludeOutput) {
      myExcludeOutput = excludeOutput;
      fireElementChanged();
    }
  }
}
