/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.util.xml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.Stack;
import net.n3.nanoxml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Properties;

/**
 * @author mike
 */
public class NanoXmlUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.NanoXmlUtil");

  private NanoXmlUtil() {
  }

  private static MyXMLReader createReader(PsiFile psiFile) throws IOException {
    return new MyXMLReader(new StringReader(psiFile.getText()));
  }

  public static void parseFile(PsiFile psiFile, final IXMLBuilder builder) {
    MyXMLReader reader = null;
    try {
      reader = createReader(psiFile);
      if (reader == null) return;
      
      parse(reader, builder);
    }
    catch (IOException e) {
    }
    finally {
      if (reader != null) {
        try {
          reader.close();
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
  }

  public static void parse(final InputStream is, final IXMLBuilder builder) {
    try {
      parse(new MyXMLReader(is), builder);
    }
    catch(IOException e) {
      LOG.error(e);
    }
  }

  public static void parse(final Reader reader, final IXMLBuilder builder) {
    try {
      parse(new MyXMLReader(reader), builder);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  private static void parse(final MyXMLReader r, final IXMLBuilder builder) {
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
        if (e.getException() instanceof ParserStoppedException) return;
        LOG.debug(e);
      }
    }
    catch (ClassNotFoundException e) {
      LOG.error(e);
    }
    catch (InstantiationException e) {
      LOG.error(e);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
    finally {
      try {
        r.close();
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  @NotNull
  public static XmlFileHeader parseHeader(VirtualFile file) {
    try {
      return parseHeaderWithException(file);
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }

  @NotNull
  public static XmlFileHeader parseHeaderWithException(final VirtualFile file) throws IOException {
    return parseHeader(new MyXMLReader(file.getInputStream()));
  }

  @NotNull
  public static XmlFileHeader parseHeader(final InputStream inputStream) {
    try {
      return parseHeader(new MyXMLReader(inputStream));
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }

  @NotNull
  public static XmlFileHeader parseHeader(PsiFile file) {
    try {
      return parseHeader(createReader(file));
    }
    catch (IOException e) {
      return XmlFileHeader.EMPTY;
    }
  }

  @NotNull
  private static XmlFileHeader parseHeader(final MyXMLReader r) {
    final RootTagInfoBuilder builder = new RootTagInfoBuilder();
    parse(r, builder);
    return new XmlFileHeader(builder.getRootTagName(), builder.getNamespace(), r.publicId, r.systemId);
  }

  public static String createLocation(@NonNls String ...tagNames) {
    StringBuffer result = new StringBuffer();
    for (String tagName : tagNames) {
      result.append(".");
      result.append(tagName);
    }

    return result.toString();
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

  public static class BaseXmlBuilder extends IXMLBuilderAdapter {
    private final Stack<String> myLocation = new Stack<String>();

    public void startBuilding(String systemID, int lineNr) throws Exception {
      myLocation.push("");
    }

    public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) throws Exception {
      myLocation.push(myLocation.peek() + "." + name);
    }

    public void endElement(String name, String nsPrefix, String nsURI) throws Exception {
      myLocation.pop();
    }

    protected static String readText(final Reader reader) throws IOException {
      return new String(StreamUtil.readTextAndConvertSeparators(reader));
    }

    protected String getLocation() {
      return myLocation.peek();
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
    private Reader myDocumentReader;
    private InputStream myStream;

    public MyXMLReader(final Reader documentReader) {
      super(documentReader);
      myDocumentReader = documentReader;
    }

    public MyXMLReader(InputStream stream) throws IOException {
      super(stream);
      myStream = stream;
    }

    @Override
    public Reader openStream(String publicId, String systemId) throws IOException {
      this.publicId = StringUtil.isEmpty(publicId) ? null : publicId;
      this.systemId = StringUtil.isEmpty(systemId) ? null : systemId;

      return new StringReader(" ");
    }

    public void close() throws IOException {
      if (myStream != null) myStream.close();
      if (myDocumentReader != null) myDocumentReader.close();
    }
  }

  public static class ParserStoppedException extends RuntimeException {
    @Override
    public Throwable fillInStackTrace() {
      return this;
    }
  }

  private static class RootTagInfoBuilder implements IXMLBuilder {
    private String myRootTagName;
    private String myNamespace;

    public void startBuilding(final String systemID, final int lineNr) throws Exception {
    }

    public void newProcessingInstruction(final String target, final Reader reader) throws Exception {
    }

    public void startElement(final String name, final String nsPrefix, final String nsURI, final String systemID, final int lineNr) throws Exception {
      myRootTagName = name;
      myNamespace = nsURI;
      throw new NanoXmlUtil.ParserStoppedException();
    }

    public void addAttribute(final String key, final String nsPrefix, final String nsURI, final String value, final String type) throws Exception {
    }

    public void elementAttributesProcessed(final String name, final String nsPrefix, final String nsURI) throws Exception {
    }

    public void endElement(final String name, final String nsPrefix, final String nsURI) throws Exception {
    }

    public void addPCData(final Reader reader, final String systemID, final int lineNr) throws Exception {
    }

    public String getNamespace() {
      return myNamespace;
    }

    public String getRootTagName() {
      return myRootTagName;
    }

    public String getResult() {
      return myRootTagName;
    }
  }

}
