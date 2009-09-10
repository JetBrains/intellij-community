
import javax.servlet.jsp.tagext.TagExtraInfo;
import javax.servlet.jsp.tagext.VariableInfo;
import javax.servlet.jsp.tagext.TagData;

/**
 * @author mike
 */
public class TestTEI extends TagExtraInfo {
  public VariableInfo[] getVariableInfo(TagData tagData) {
    return new VariableInfo[] { new VariableInfo("testVar", tagData.getAttributeString("class"), true, VariableInfo.AT_BEGIN) };
  }
}
