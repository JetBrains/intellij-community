package com.intellij.uiDesigner.lw;

import com.intellij.uiDesigner.core.AbstractLayout;
import org.jdom.Element;

import javax.swing.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class LwTabbedPane extends LwContainer{
  public LwTabbedPane() throws Exception{
    super(JTabbedPane.class.getName());
  }

  protected AbstractLayout createInitialLayout(){
    return null;
  }

  public void read(final Element element, final PropertiesProvider provider) throws Exception {
    readId(element);
    readBinding(element);

    // Constraints and properties
    readConstraints(element);
    readProperties(element, provider);

    // Border
    readBorder(element);
    
    readChildren(element, provider);
  }

  protected void readConstraintsForChild(final Element element, final LwComponent component) {
    final Element constraintsElement = LwXmlReader.getRequiredChild(element, "constraints");
    final Element tabbedPaneChild = LwXmlReader.getRequiredChild(constraintsElement, "tabbedpane");
    final String tabName = LwXmlReader.getString(tabbedPaneChild, "title");
    if (tabName == null) {
      final String resBundle = LwXmlReader.getString(tabbedPaneChild, "title-resource-bundle");
      final String key = LwXmlReader.getString(tabbedPaneChild, "title-key");
      if (resBundle == null || key == null) {
        throw new IllegalArgumentException("either attribute 'title' or 'title-resource-bundle' and 'title-key' are required: "+tabbedPaneChild);
      }
      component.setCustomLayoutConstraints(new Constraints(new StringDescriptor(resBundle, key)));
    }
    else {
      component.setCustomLayoutConstraints(new Constraints(StringDescriptor.create(tabName)));
    }
  }

  public static final class Constraints {
    /**
     * never null
     */ 
    public final StringDescriptor myTitle;

    public Constraints(final StringDescriptor title){
      if (title == null){
        throw new IllegalArgumentException("title cannot be null");
      }
      myTitle = title;
    }
  }
}
