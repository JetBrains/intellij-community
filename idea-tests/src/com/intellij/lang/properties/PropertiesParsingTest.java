package com.intellij.lang.properties;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.testFramework.ParsingTestCase;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 29, 2005
 * Time: 9:04:05 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesParsingTest extends ParsingTestCase {
  public PropertiesParsingTest() {
    super("", "properties");
  }

  protected String testDataPath() {
    return PathManagerEx.getTestDataPath() + "/propertiesFile";
  }

  public void testProp1() throws Exception { doTest(true); }
  public void testComments() throws Exception { doTest(true); }
}
