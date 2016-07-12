package com.intellij.help.impl;

import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.intention.impl.config.IntentionActionMetaData;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerImpl;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.codeInsight.intention.impl.config.TextDescriptor;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.TimeoutUtil;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;

public class IntentionDump implements ApplicationStarter {
  @Override
  public String getCommandName() {
    return "intention-dump";
  }

  @Override
  public void premain(String[] args) {

  }

  @Override
  public void main(String[] args) {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    try {
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document document = builder.newDocument();
      Element intentions = document.createElement("Intentions");
      document.appendChild(intentions);
      while (((IntentionManagerImpl)IntentionManager.getInstance()).hasActiveRequests()) {
        TimeoutUtil.sleep(100);
      }
      for (IntentionActionMetaData actionMetaData : IntentionManagerSettings.getInstance().getMetaData()) {
        Element intention = document.createElement("Intention");
        intention.setAttribute("categories", StringUtil.join(actionMetaData.myCategory, ","));
        intention.setAttribute("name", actionMetaData.getFamily());
        Element description = document.createElement("description");
        CDATASection descriptionSection = document.createCDATASection(escapeCDATA(actionMetaData.getDescription().getText()));
        description.appendChild(descriptionSection);
        intention.appendChild(description);
        TextDescriptor[] beforeDescriptors = actionMetaData.getExampleUsagesBefore();
        if (beforeDescriptors.length > 0) {
          Element before = document.createElement("before");
          CDATASection beforeSection = document.createCDATASection(escapeCDATA(beforeDescriptors[0].getText()));
          before.appendChild(beforeSection);
          intention.appendChild(before);
        }
        TextDescriptor[] afterDescriptors = actionMetaData.getExampleUsagesAfter();
        if (afterDescriptors.length > 0) {
          Element after = document.createElement("after");
          CDATASection afterSection = document.createCDATASection(escapeCDATA(afterDescriptors[0].getText()));
          after.appendChild(afterSection);
          intention.appendChild(after);
        }
        intentions.appendChild(intention);
      }

      Transformer transformer = TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      DOMSource source = new DOMSource(document);
      final String path = args.length == 2 ? args[1] : PathManager.getHomePath() + File.separator + "AllIntentions.xml";
      StreamResult console = new StreamResult(new File(path));
      transformer.transform(source, console);

      System.exit(0);
    }
    catch (ParserConfigurationException e) {
      e.printStackTrace();
    }
    catch (TransformerConfigurationException e) {
      e.printStackTrace();
    }
    catch (TransformerException e) {
      e.printStackTrace();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static String escapeCDATA(String cData) {
    return cData.replaceAll("\\]", "&#x005D;").replaceAll("\\[", "&#x005B;");
  }
}
