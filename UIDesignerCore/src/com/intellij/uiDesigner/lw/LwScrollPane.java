package com.intellij.uiDesigner.lw;

import com.intellij.uiDesigner.core.AbstractLayout;
import org.jdom.Element;

import javax.swing.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class LwScrollPane extends LwContainer{
  public LwScrollPane() throws Exception{
    super(JScrollPane.class.getName());
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

  protected void readConstraintsForChild(final Element element, final LwComponent component) {}
}
