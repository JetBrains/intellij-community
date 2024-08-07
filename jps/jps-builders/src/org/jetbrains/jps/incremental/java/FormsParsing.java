// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.compiler.Utils;
import net.n3.nanoxml.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Properties;

@ApiStatus.Internal
public final class FormsParsing {
  private static final Logger LOG = Logger.getInstance(FormsParsing.class);
  private static final String FORM_TAG = "form";

  private FormsParsing() {
  }

  public static String readBoundClassName(File formFile) throws IOException, AlienFormFileException {
    try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(formFile))) {
      final Ref<String> result = new Ref<>(null);
      final Ref<Boolean> isAlien = new Ref<>(Boolean.FALSE);
      parse(in, new IXMLBuilderAdapter() {
        @Override
        public void startElement(final String elemName, final String nsPrefix, final String nsURI, final String systemID, final int lineNr) {
          if (!FORM_TAG.equalsIgnoreCase(elemName)) {
            stop();
          }
          boolean alien = !Utils.FORM_NAMESPACE.equalsIgnoreCase(nsURI);
          if (alien) {
            isAlien.set(Boolean.TRUE);
            stop();
          }
        }

        @Override
        public void addAttribute(final String key, final String nsPrefix, final String nsURI, final String value, final String type) {
          if (UIFormXmlConstants.ATTRIBUTE_BIND_TO_CLASS.equals(key)) {
            result.set(value);
            stop();
          }
        }

        @Override
        public void elementAttributesProcessed(final String name, final String nsPrefix, final String nsURI) {
          stop();
        }
      });
      if (isAlien.get()) {
        throw new AlienFormFileException();
      }
      return result.get();
    }
  }

  public static void parse(final InputStream is, final IXMLBuilder builder) {
    try (is) {
      parse(new MyXMLReader(is), builder);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public static void parse(final StdXMLReader r, final IXMLBuilder builder) {
    StdXMLParser parser = new StdXMLParser(r, builder, new EmptyValidator(), new EmptyEntityResolver());
    try {
      parser.parse();
    }
    catch (XMLException e) {
      if (e.getException() instanceof ParserStoppedException) {
        return;
      }
      LOG.debug(e);
    }
  }

  private static final class EmptyValidator extends NonValidator {
    @Override
    public void elementStarted(String name, String systemId, int lineNr) {
    }

    @Override
    public void attributeAdded(String key, String value, String systemId, int lineNr) {
    }

    @Override
    public void elementAttributesProcessed(String name, Properties extraAttributes, String systemId, int lineNr) {
    }
  }

  private static final class EmptyEntityResolver implements IXMLEntityResolver {
    @Override
    public void addInternalEntity(String name, String value) {
    }

    @Override
    public void addExternalEntity(String name, String publicID, String systemID) {
    }

    @Override
    public Reader getEntity(StdXMLReader xmlReader, String name) {
      return new StringReader("");
    }

    @Override
    public boolean isExternalEntity(String name) {
      return false;
    }
  }

  private static final class MyXMLReader extends StdXMLReader {
    MyXMLReader(InputStream stream) throws IOException {
      super(stream);
    }

    @Override
    public Reader openStream(String publicId, String systemId) {
      return new StringReader(" ");
    }
  }

  @ApiStatus.Internal
  public static class IXMLBuilderAdapter implements IXMLBuilder {

    @Override
    public void startBuilding(final String systemID, final int lineNr) {
    }

    @Override
    public void newProcessingInstruction(final String target, final Reader reader) {

    }

    @Override
    public void startElement(final String name, final String nsPrefix, final String nsURI, final String systemID, final int lineNr) {
    }

    @Override
    public void addAttribute(final String key, final String nsPrefix, final String nsURI, final String value, final String type) {
    }

    @Override
    public void elementAttributesProcessed(final String name, final String nsPrefix, final String nsURI) {
    }

    @Override
    public void endElement(final String name, final String nsPrefix, final String nsURI) {
    }

    @Override
    public void addPCData(final Reader reader, final String systemID, final int lineNr) {
    }

    @Override
    public @Nullable Object getResult() {
      return null;
    }

    protected static void stop() {
      throw new ParserStoppedException();
    }
  }

  @ApiStatus.Internal
  public static final class ParserStoppedException extends RuntimeException {
    @Override
    public synchronized Throwable fillInStackTrace() {
      return this;
    }
  }

}
