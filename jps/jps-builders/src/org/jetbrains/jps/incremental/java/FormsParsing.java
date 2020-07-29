// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.uiDesigner.UIFormXmlConstants;
import net.n3.nanoxml.*;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Properties;

/**
 * @author Eugene Zhuravlev
 */
public final class FormsParsing {
  private static final Logger LOG = Logger.getInstance(FormsParsing.class);
  private static final String FORM_TAG = "form";

  private FormsParsing() {
  }

  public static String readBoundClassName(File formFile) throws IOException {
    try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(formFile))) {
      final Ref<String> result = new Ref<>(null);
      parse(in, new IXMLBuilderAdapter() {
        @Override
        public void startElement(final String elemName, final String nsPrefix, final String nsURI, final String systemID, final int lineNr)
          throws Exception {
          if (!FORM_TAG.equalsIgnoreCase(elemName)) {
            stop();
          }
        }

        @Override
        public void addAttribute(final String key, final String nsPrefix, final String nsURI, final String value, final String type)
          throws Exception {
          if (UIFormXmlConstants.ATTRIBUTE_BIND_TO_CLASS.equals(key)) {
            result.set(value);
            stop();
          }
        }

        @Override
        public void elementAttributesProcessed(final String name, final String nsPrefix, final String nsURI) throws Exception {
          stop();
        }
      });
      return result.get();
    }
  }

  public static void parse(final InputStream is, final IXMLBuilder builder) {
    try {
      parse(new MyXMLReader(is), builder);
    }
    catch(IOException e) {
      LOG.error(e);
    }
    finally {

      try {
        is.close();
      }
      catch (IOException ignore) {

      }
    }
  }

  public static void parse(final IXMLReader r, final IXMLBuilder builder) {
    try {
      final IXMLParser parser = XMLParserFactory.createDefaultXMLParser();
      parser.setReader(r);
      parser.setBuilder(builder);
      parser.setValidator(new EmptyValidator());
      parser.setResolver(new EmptyEntityResolver());
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
    catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
      LOG.error(e);
    }
  }

  private static class EmptyValidator extends NonValidator {
    private IXMLEntityResolver myParameterEntityResolver;

    @Override
    public void setParameterEntityResolver(IXMLEntityResolver resolver) {
      myParameterEntityResolver = resolver;
    }

    @Override
    public IXMLEntityResolver getParameterEntityResolver() {
      return myParameterEntityResolver;
    }

    @Override
    public void parseDTD(String publicID, IXMLReader reader, IXMLEntityResolver entityResolver, boolean external) throws Exception {
      if (!external) {
        //super.parseDTD(publicID, reader, entityResolver, external);
        int cnt = 1;
        for (char ch = reader.read(); !(ch == ']' && --cnt == 0); ch = reader.read()) {
          if (ch == '[') cnt ++;
        }
      }
      else {
        int origLevel = reader.getStreamLevel();

        while (true) {
          char ch = reader.read();

          if (reader.getStreamLevel() < origLevel) {
            reader.unread(ch);
            return; // end external DTD
          }
        }
      }
    }

    @Override
    public void elementStarted(String name, String systemId, int lineNr) {
    }

    @Override
    public void elementEnded(String name, String systemId, int lineNr) {
    }

    @Override
    public void attributeAdded(String key, String value, String systemId, int lineNr) {
    }

    @Override
    public void elementAttributesProcessed(String name, Properties extraAttributes, String systemId, int lineNr) {
    }

    @Override
    public void PCDataAdded(String systemId, int lineNr)  {
    }
  }

  private static class EmptyEntityResolver implements IXMLEntityResolver {
    @Override
    public void addInternalEntity(String name, String value) {
    }

    @Override
    public void addExternalEntity(String name, String publicID, String systemID) {
    }

    @Override
    public Reader getEntity(IXMLReader xmlReader, String name) throws XMLParseException {
      return new StringReader("");
    }

    @Override
    public boolean isExternalEntity(String name) {
      return false;
    }
  }

  private static class MyXMLReader extends StdXMLReader {
    private String publicId;
    private String systemId;

    MyXMLReader(final Reader documentReader) {
      super(documentReader);
    }

    MyXMLReader(InputStream stream) throws IOException {
      super(stream);
    }

    @Override
    public Reader openStream(String publicId, String systemId) throws IOException {
      this.publicId = StringUtil.isEmpty(publicId) ? null : publicId;
      this.systemId = StringUtil.isEmpty(systemId) ? null : systemId;

      return new StringReader(" ");
    }
  }

  public static class IXMLBuilderAdapter implements IXMLBuilder {

    @Override
    public void startBuilding(final String systemID, final int lineNr) throws Exception {
    }

    @Override
    public void newProcessingInstruction(final String target, final Reader reader) throws Exception {

    }

    @Override
    public void startElement(final String name, final String nsPrefix, final String nsURI, final String systemID, final int lineNr)
        throws Exception {
    }

    @Override
    public void addAttribute(final String key, final String nsPrefix, final String nsURI, final String value, final String type)
        throws Exception {
    }

    @Override
    public void elementAttributesProcessed(final String name, final String nsPrefix, final String nsURI) throws Exception {
    }

    @Override
    public void endElement(final String name, final String nsPrefix, final String nsURI) throws Exception {
    }

    @Override
    public void addPCData(final Reader reader, final String systemID, final int lineNr) throws Exception {
    }

    @Override
    @Nullable
    public Object getResult() throws Exception {
      return null;
    }

    protected static void stop() {
      throw new ParserStoppedException();
    }
  }

  public static class ParserStoppedException extends RuntimeException {
    @Override
    public Throwable fillInStackTrace() {
      return this;
    }
  }

}
