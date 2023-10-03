// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.InspectionsReportConverter;
import com.intellij.codeInspection.InspectionsResultUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.transform.*;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;

/**
 * @author Roman.Chernyatchik
 */
public class XSLTReportConverter implements InspectionsReportConverter {
  private final String myXSLTSchemePath;

  public XSLTReportConverter(final @NotNull String xsltSchemePath) {
    myXSLTSchemePath = xsltSchemePath;
  }

  @Override
  public String getFormatName() {
    return "xslt";
  }

  @Override
  public boolean useTmpDirForRawData() {
    return true;
  }

  @Override
  public void convert(final @NotNull String rawDataDirectoryPath,
                      final @Nullable String outputPath,
                      final @NotNull Map<String, Tools> tools,
                      final @NotNull List<? extends File> inspectionsResults) throws InspectionsReportConverter.ConversionException {

    if (outputPath == null) {
      throw new ConversionException("Output path isn't specified.");
    }

    final SAXTransformerFactory transformerFactory = (SAXTransformerFactory)TransformerFactory.newDefaultInstance();

    final Source xslSource;
    final Transformer transformer;
    try {
      final File xsltSchemeFile = new File(myXSLTSchemePath);
      if (!xsltSchemeFile.exists()) {
        throw new ConversionException("Cannot find XSLT scheme: " + myXSLTSchemePath);
      }
      xslSource = new StreamSource(xsltSchemeFile);
      transformer = transformerFactory.newTransformer(xslSource);
    }
    catch (TransformerConfigurationException e) {
      throw new ConversionException("Fail to load XSLT scheme.");
    }


    final Writer w;
    final File outputFile = new File(outputPath);
    try {
      w = new FileWriter(outputFile);
    }
    catch (IOException e) {
      throw new ConversionException("Cannot edit file: " + outputFile.getPath());
    }

    try {
      for (File inspectionData : inspectionsResults) {
        if (inspectionData.isDirectory()) {
          warn("Folder isn't expected here: " + inspectionData.getName());
          continue;
        }
        final String fileNameWithoutExt = FileUtilRt.getNameWithoutExtension(inspectionData.getName());
        if (InspectionsResultUtil.DESCRIPTIONS.equals(fileNameWithoutExt) || fileNameWithoutExt.endsWith(InspectionsResultUtil.AGGREGATE)) {
          continue;
        }

        // Transform results:
        try {
          transformer.transform(new StreamSource(inspectionData), new StreamResult(w));
        }
        catch (TransformerException e) {
          throw new ConversionException("Cannot apply XSL transformation: " + e.getMessage());
        }
      }
    }
    finally {
      try {
        w.close();
      }
      catch (IOException e) {
        warn("Cannot save inspection results: " + e.getMessage());
      }
    }
  }

  private void warn(final @NotNull String msg) {
    System.err.println(msg);
  }
}
