package com.intellij.uiDesigner.lw;

import com.intellij.uiDesigner.core.Spacer;
import org.jdom.Element;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class LwVSpacer extends LwAtomicComponent {
  public LwVSpacer() throws Exception{
    super(Spacer.class.getName());
  }
  
  public void read(final Element element, final PropertiesProvider provider) throws Exception{
    readId(element);
    readConstraints(element);
  }
}
