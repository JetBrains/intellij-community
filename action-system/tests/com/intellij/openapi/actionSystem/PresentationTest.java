package com.intellij.openapi.actionSystem;

import junit.framework.Assert;
import junit.framework.TestCase;

/**
 * Created in IntelliJ IDEA.
 * By: Alexander.Chernikov
 * When: 10.10.2006, 20:19:35
 */
public class PresentationTest extends TestCase {

  private String[] inputTextsUnderscores = new String[]{"No mnemonic", "_First char",
    "S_econd char", "Pre-last and not unique ch_ar", "Last cha_r", "Too late_", "Do__uble", "Dou_&ble",
    "Complete double__", "Complete double_&", "Repea_te_d", "Re_peate&d"
    /*,
    "Alphanumeric only _! _@ _# _$ _% _^ _& _* _( _) _- __ _= _+ _| _\\ _/ _0"*/};
  private String[] inputTextsAmpersands = new String[]{"No mnemonic", "&First char", "S&econd char",
    "Pre-last and not unique ch&ar", "Last cha&r", "Too late&", "Do&_uble", "Dou&&ble", "Complete double&_",
    "Complete double&&", "Repea&te_d", "Re&peate&d"
    /*,
    "Alphanumeric only &! &@ &# &$ &% &^ && &* &( &) &- &_ &= &+ &| &\\ &/ &0"*/};
  private String[] menuTexts = new String[]{"No mnemonic", "First char", "Second char",
    "Pre-last and not unique char", "Last char", "Too late", "Do_uble", "Dou&ble", "Complete double_",
    "Complete double&", "Repeate_d", "Repeate&d"};
  private int[] mnemonics = new int[]{0, 'F', 'E', 'A', 'R', 0, 0, 0, 0, 0, 'T', 'P'};
  private int[] indeces = new int[]{-1, 0, 1, 26, 8, -1, -1, -1, -1, -1, 5, 2};
  private String[] fullMenuTexts = new String[]{"No mnemonic", "_First char", "S_econd char",
    "Pre-last and not unique ch_ar", "Last cha_r", "Too late", "Do_uble", "Dou&ble", "Complete double_",
    "Complete double&", "Repea_te_d", "Re_peate&d"};

  public void testPresentationSetText() {
    for (int i = 0; i < inputTextsUnderscores.length; i++) {
      Presentation p = new Presentation();
      p.setText(inputTextsUnderscores[i]);
      Assert.assertEquals(menuTexts[i], p.getText());
      Assert.assertEquals(mnemonics[i], p.getMnemonic());
      Assert.assertEquals(indeces[i], p.getDisplayedMnemonicIndex());
      Assert.assertEquals(fullMenuTexts[i], p.getTextWithMnemonic());

      p.setText(inputTextsAmpersands[i]);
      Assert.assertEquals(menuTexts[i], p.getText());
      Assert.assertEquals(mnemonics[i], p.getMnemonic());
      Assert.assertEquals(indeces[i], p.getDisplayedMnemonicIndex());
      Assert.assertEquals(fullMenuTexts[i], p.getTextWithMnemonic());
    }
  }
}
