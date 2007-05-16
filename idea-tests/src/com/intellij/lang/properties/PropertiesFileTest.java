package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesElementFactory;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import java.util.List;

/**
 * @author max
 */
public class PropertiesFileTest extends LightIdeaTestCase {
  private Property myPropertyToAdd;

  protected void setUp() throws Exception {
    super.setUp();
    myPropertyToAdd = PropertiesElementFactory.createProperty(getProject(), "kkk", "vvv");
  }

  public void testAddPropertyAfterComment() throws Exception {
    PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "#xxxxx");
    propertiesFile.addProperty(myPropertyToAdd);

    List<Property> properties = propertiesFile.getProperties();
    Property added = properties.get(0);
    assertPropertyEquals(added, myPropertyToAdd.getName(), myPropertyToAdd.getValue());
  }

  private static void assertPropertyEquals(final Property property, @NonNls String name, @NonNls String value) {
    assertEquals(name, property.getName());
    assertEquals(value, property.getValue());
  }

  public void testAddPropertyAfterProperty() throws Exception {
    PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "xxx=yyy");
    propertiesFile.addProperty(myPropertyToAdd);

    List<Property> properties = propertiesFile.getProperties();
    assertEquals(2, properties.size());
    assertPropertyEquals(properties.get(0), "xxx", "yyy");
    assertPropertyEquals(properties.get(1), myPropertyToAdd.getName(), myPropertyToAdd.getValue());
  }
  public void testDeleteProperty() throws Exception {
    PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "xxx=yyy\n#s\nzzz=ttt\n\n");

    List<Property> properties = propertiesFile.getProperties();
    assertEquals(2, properties.size());
    assertPropertyEquals(properties.get(0), "xxx", "yyy");
    assertPropertyEquals(properties.get(1), "zzz", "ttt");

    properties.get(1).delete();
    List<Property> propertiesAfter = propertiesFile.getProperties();
    assertEquals(1, propertiesAfter.size());
    assertPropertyEquals(propertiesAfter.get(0), "xxx", "yyy");
  }

  public void testDeletePropertyWhitespaceAround() throws Exception {
    PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "xxx=yyy\nxxx2=tyrt\nxxx3=ttt\n\n");

    Property property = propertiesFile.findPropertyByKey("xxx2");
    property.delete();

    assertEquals("xxx=yyy\nxxx3=ttt\n\n", propertiesFile.getText());
  }
  public void testDeletePropertyWhitespaceAhead() throws Exception {
    PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "xxx=yyy\nxxx2=tyrt\nxxx3=ttt\n\n");

    Property property = propertiesFile.findPropertyByKey("xxx");
    property.delete();

    assertEquals("xxx2=tyrt\nxxx3=ttt\n\n", propertiesFile.getText());
  }

  public void testAddToEnd() throws IncorrectOperationException {
    PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "a=b\\nccc");
    assertEquals(1,propertiesFile.getProperties().size());
    propertiesFile.addProperty(myPropertyToAdd);
    assertEquals("a=b\\nccc\nkkk=vvv", propertiesFile.getText());
  }

  public void testUnescapedValue() {
    PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "a=b\\nc\\u0063c");
    assertEquals("b\nccc", propertiesFile.getProperties().get(0).getUnescapedValue());
  }

  public void testUnescapedLineBreak() {
    PropertiesFile propertiesFile = PropertiesElementFactory.createPropertiesFile(getProject(), "a=b\\\n\t  c");
    assertEquals("bc", propertiesFile.getProperties().get(0).getUnescapedValue());
  }
}
