package com.intellij.configurationStore.xml;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.SerializationFilter;
import com.intellij.util.xmlb.annotations.Transient;
import junit.framework.TestCase;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"deprecation"})
public class XmlSerializerWithDefaultJDOMExternalizerCompatibilityTest extends TestCase {
  public void testCompatibility() throws Exception {
    final MyBean bean = new MyBean();
    assertCompatibleSerialization(bean, "MyBean");
  }

  private static void assertCompatibleSerialization(final Object data, final String rootTagName) throws Exception {
    assertEquals(serializeWithJDom(data, rootTagName), serializeWithXmlSerializer(data));
  }

  private static String serializeWithXmlSerializer(final Object data) throws Exception {
    Element element = serialize(data, null);

    String s = JDOMUtil.writeElement(element, "\n");
    return StringUtil.trimStart(s, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>").trim();
  }

  private static String serializeWithJDom(final Object data, String rootTagName) throws Exception {
    final Element jDomRoot = new Element(rootTagName);

    if (data instanceof com.intellij.openapi.util.JDOMExternalizable) {
      ((com.intellij.openapi.util.JDOMExternalizable)data).writeExternal(jDomRoot);
    }
    else {
      com.intellij.openapi.util.DefaultJDOMExternalizer.writeExternal(data, jDomRoot);
    }

    return JDOMUtil.writeElement(jDomRoot, "\n").trim();
  }

  private static Element serialize(Object bean, @Nullable SerializationFilter filter) {
    return XmlSerializer.serialize(bean, filter);
  }

  @SuppressWarnings("unused")
  private static class MyBean {
    public int intField = 0;
    public int intField2 = 1;
    public boolean booleanField = false;
    public boolean booleanField2 = true;
    public String stringField;
    public String stringField2 = "a";
    public float floatField;
    public float floatField2 = 1;
    public transient int transientField;
    @Transient
    public int transientField2;
  }
}
