package com.intellij.openapi.execution;

import com.intellij.execution.configurations.ParametersList;
import com.intellij.util.Assertion;
import com.intellij.util.ArrayUtil;
import junit.framework.TestCase;

/**
 * @author dyoma
 */
public class ParametersListTest extends TestCase {
  private final Assertion CHECK = new Assertion();

  public void testAddParametersString() {
    checkTokenizer("a b c", new String[]{"a", "b", "c"});
    checkTokenizer("a \"b\"", new String[]{"a", "b"});
    checkTokenizer("a \"b\\\"", new String[]{"a", "b\\\""});
    checkTokenizer("a \"\"", new String[]{"a", "\"\""}); // Bug #12169
    checkTokenizer("a \"x\"", new String[]{"a", "x"});
    checkTokenizer("a \"\" b", new String[]{"a", "\"\"", "b"});
  }

  private void checkTokenizer(String parmsString, String[] expected) {
    ParametersList params = new ParametersList();
    params.addParametersString(parmsString);
    String[] strings = params.getList().toArray(ArrayUtil.EMPTY_STRING_ARRAY);
    CHECK.compareAll(expected, strings);
  }
}
