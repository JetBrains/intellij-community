package com.intellij.uiDesigner.lw;

import org.jdom.Element;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class LwAtomicComponent extends LwComponent {
  public LwAtomicComponent(final String className){
    super(className);
  }

  public void read(final Element element, final PropertiesProvider provider) throws Exception{
    readId(element);
    readBinding(element);
    readConstraints(element);
    readProperties(element, provider);
  }
}
