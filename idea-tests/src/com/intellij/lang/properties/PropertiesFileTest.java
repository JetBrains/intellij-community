package com.intellij.lang.properties;

import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.lang.properties.psi.PropertiesElementFactory;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;

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

  private static void assertPropertyEquals(final Property property, String name, String value) {
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
}
