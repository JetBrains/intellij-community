package com.intellij.conversion.impl.ui;

import com.intellij.conversion.impl.ConversionContextImpl;
import com.intellij.conversion.impl.ConversionRunner;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLEditorKit;
import java.util.List;

/**
 * @author nik
 */
public class StartConversionStep extends AbstractConversionStep {
  private JPanel myMainPanel;
  private JTextPane myTextPane;

  public StartConversionStep(ConversionContextImpl context, final List<ConversionRunner> conversionRunners) {
    JLabel templateLabel = new JLabel();
    myTextPane.setFont(templateLabel.getFont());
    myTextPane.setContentType("text/html");
    myTextPane.setEditorKit(new HTMLEditorKit());
    myTextPane.setEditable(false);
    myTextPane.setBackground(templateLabel.getBackground());
    myTextPane.setForeground(templateLabel.getForeground());
    myTextPane.setText(IdeBundle.message("label.text.project.has.older.format",
                                           context.getProjectFile().getName()));

    myTextPane.addHyperlinkListener(new HyperlinkListener() {
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          @NonNls StringBuilder descriptions = new StringBuilder("<html>The following conversions will be performed:<br>");
          for (ConversionRunner runner : conversionRunners) {
            descriptions.append(runner.getProvider().getConversionDescription()).append("<br>");
          }
          descriptions.append("</html>");
          Messages.showInfoMessage(descriptions.toString(), IdeBundle.message("dialog.title.convert.project"));
        }
      }
    });
  }

  public JComponent getComponent() {
    return myMainPanel;
  }
}
