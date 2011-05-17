package org.jetbrains.jps.gwt;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public class GwtModulesSearcherTest extends TestCase {
  public void test() {
    List<String> modules = GwtModulesSearcher.findGwtModules(Arrays.asList("plugins/gwt/testData/modules"));
    assertEquals(new HashSet<String>(Arrays.asList("com.app.App", "com.app.InvalidXml")),
               new HashSet<String>(modules));
  }
}
