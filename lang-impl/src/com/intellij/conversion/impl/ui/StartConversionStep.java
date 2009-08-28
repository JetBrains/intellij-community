package com.intellij.conversion.impl.ui;

import com.intellij.conversion.impl.ConversionRunner;
import com.intellij.conversion.impl.ConversionContextImpl;
import com.intellij.ide.IdeBundle;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.List;

/**
 * @author nik
 */
public class StartConversionStep extends AbstractConversionStep {
  private JPanel myMainPanel;
  private JLabel myConvertersDescriptionsLabel;
  private JLabel myTitleLabel;

  public StartConversionStep(ConversionContextImpl context, List<ConversionRunner> conversionRunners) {
    myTitleLabel.setText(IdeBundle.message("label.project.has.older.format.the.following.conversions.will.be.performed.text",
                                           context.getProjectFile().getName()));

    @NonNls StringBuilder descriptions = new StringBuilder("<html>");
    for (ConversionRunner runner : conversionRunners) {
      descriptions.append(runner.getProvider().getConversionDescription()).append("<br>");
    }
    descriptions.append("</html>");
    myConvertersDescriptionsLabel.setText(descriptions.toString());
  }

  public JComponent getComponent() {
    return myMainPanel;
  }
}
