/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
public class FormsParsing {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.java.FormsParsing");
  private static final String FORM_TAG = "form";

  private FormsParsing() {
  }

  public static String readBoundClassName(File formFile) throws IOException {
    final BufferedInputStream in = new BufferedInputStream(new FileInputStream(formFile));
    try {
      final Ref<String> result = new Ref<>(null);
      parse(in, new IXMLBuilderAdapter() {
        public void startElement(final String elemName, final String nsPrefix, final String nsURI, final String systemID, final int lineNr)
          throws Exception {
          if (!FORM_TAG.equalsIgnoreCase(elemName)) {
            stop();
          }
        }

        public void addAttribute(final String key, final String nsPrefix, final String nsURI, final String value, final String type)
          throws Exception {
          if (UIFormXmlConstants.ATTRIBUTE_BIND_TO_CLASS.equals(key)) {
            result.set(value);
            stop();
          }
        }

        public void elementAttributesProcessed(final String name, final String nsPrefix, final String nsURI) throws Exception {
          stop();
        }
      });
      return result.get();
    }
    finally {
      in.close();
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

    public void setParameterEntityResolver(IXMLEntityResolver resolver) {
      myParameterEntityResolver = resolver;
    }

    public IXMLEntityResolver getParameterEntityResolver() {
      return myParameterEntityResolver;
    }

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

    public void elementStarted(String name, String systemId, int lineNr) {
    }

    public void elementEnded(String name, String systemId, int lineNr) {
    }

    public void attributeAdded(String key, String value, String systemId, int lineNr) {
    }

    public void elementAttributesProcessed(String name, Properties extraAttributes, String systemId, int lineNr) {
    }

    public void PCDataAdded(String systemId, int lineNr)  {
    }
  }

  private static class EmptyEntityResolver implements IXMLEntityResolver {
    public void addInternalEntity(String name, String value) {
    }

    public void addExternalEntity(String name, String publicID, String systemID) {
    }

    public Reader getEntity(IXMLReader xmlReader, String name) throws XMLParseException {
      return new StringReader("");
    }

    public boolean isExternalEntity(String name) {
      return false;
    }
  }

  private static class MyXMLReader extends StdXMLReader {
    private String publicId;
    private String systemId;

    public MyXMLReader(final Reader documentReader) {
      super(documentReader);
    }

    public MyXMLReader(InputStream stream) throws IOException {
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

    public void startBuilding(final String systemID, final int lineNr) throws Exception {
    }

    public void newProcessingInstruction(final String target, final Reader reader) throws Exception {

    }

    public void startElement(final String name, final String nsPrefix, final String nsURI, final String systemID, final int lineNr)
        throws Exception {
    }

    public void addAttribute(final String key, final String nsPrefix, final String nsURI, final String value, final String type)
        throws Exception {
    }

    public void elementAttributesProcessed(final String name, final String nsPrefix, final String nsURI) throws Exception {
    }

    public void endElement(final String name, final String nsPrefix, final String nsURI) throws Exception {
    }

    public void addPCData(final Reader reader, final String systemID, final int lineNr) throws Exception {
    }

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
