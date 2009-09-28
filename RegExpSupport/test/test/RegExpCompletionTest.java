package test;

import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import org.intellij.lang.regexp.psi.impl.RegExpPropertyImpl;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by IntelliJ IDEA.
 * User: vnikolaenko
 * Date: 25.09.2008
 * Time: 15:10:10
 */
public class RegExpCompletionTest extends CodeInsightFixtureTestCase {

    // util methods
    private static String getInputDataFileName(String testName) {
        return Character.toUpperCase(testName.charAt(0)) + testName.substring(1) + ".regexp";
    }

    private static String getExpectedResultFileName(String testName) {
        return Character.toUpperCase(testName.charAt(0)) + testName.substring(1) + "Expected" + ".regexp";
    }

    public void testBackSlashVariants() throws Throwable {
        doBackSlashVariantsTest();
    }
  
    public void testBackSlashVariants2() throws Throwable {
        doBackSlashVariantsTest();
    }

    private void doBackSlashVariantsTest() throws Throwable {
        java.util.List<String> nameList = new ArrayList<String>(Arrays.asList("d", "D", "s", "S", "w", "W", "b", "B", "A", "G", "Z", "z", "Q", "E",
                "t", "n", "r", "f", "a", "e"));
        for (String[] stringArray : RegExpPropertyImpl.PROPERTY_NAMES) {
            nameList.add("p{" + stringArray[0] + "}");
        }
        myFixture.testCompletionVariants(getInputDataFileName(getTestName(true)), nameList.toArray(new String[nameList.size()]));
    }

  public void testPropertyVariants() throws Throwable {
        java.util.List<String> nameList = new ArrayList<String>();
        for (String[] stringArray : RegExpPropertyImpl.PROPERTY_NAMES) {
            nameList.add("{" + stringArray[0] + "}");
        }
        myFixture.testCompletionVariants(getInputDataFileName(getTestName(true)), nameList.toArray(new String[nameList.size()]));
    }

    public void testPropertyAlpha() throws Throwable {
        doTest();
    }

    public void doTest() throws Throwable {
        String inputDataFileName = getInputDataFileName(getTestName(true));
        String expectedResultFileName = getExpectedResultFileName(getTestName(true));
        myFixture.testCompletion(inputDataFileName, expectedResultFileName);
    }

    @Override
    protected String getBasePath() {
      String homePath = PathManager.getHomePath();
      File candidate = new File(homePath, "community/RegExpSupport");
      if (candidate.isDirectory()) {
        return "/community/RegExpSupport/testData/completion";
      }
      return "/RegExpSupport/testData/completion";
    }
}
