// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.xml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.Stack;
import com.intellij.util.text.CharSequenceReader;
import com.intellij.util.text.StringFactory;
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

  private static MyXMLReader createReader(PsiFile psiFile) {
    return new MyXMLReader(new CharSequenceReader(psiFile.getViewProvider().getContents()));
  }

  public static void parseFile(PsiFile psiFile, final IXMLBuilder builder) {
    MyXMLReader reader = createReader(psiFile);
    parse(reader, builder);
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

  public static void parse(final Reader reader, final IXMLBuilder builder) {
    parse(reader, builder, null);
  }

  public static void parse(@NotNull Reader reader, @NotNull IXMLBuilder builder, @Nullable IXMLValidator validator) {
    try {
      parse(new MyXMLReader(reader), builder, validator);
    }
    catch (Exception e) {
      LOG.error(e);
    }
    finally {
      try {
        reader.close();
      }
      catch (IOException ignore) {

      }
    }
  }

  public static void parse(final IXMLReader r, final IXMLBuilder builder) {
    parse(r, builder, null);
  }

  public static void parse(final IXMLReader r, final IXMLBuilder builder, @Nullable final IXMLValidator validator) {
    try {
      final IXMLParser parser = XMLParserFactory.createDefaultXMLParser();
      parser.setReader(r);
      parser.setBuilder(builder);
      parser.setValidator(validator == null ? new EmptyValidator() : validator);
      parser.setResolver(new EmptyEntityResolver());
      try {
        parser.parse();
      }
      catch (ParserStoppedXmlException ignore) {
      }
      catch (XMLException e) {
        LOG.debug(e);
      }
    }
    catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
      LOG.error(e);
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
  public static XmlFileHeader parseHeaderWithException(Reader reader) {
    return parseHeader(new MyXMLReader(reader));
  }

  @NotNull
  public static XmlFileHeader parseHeaderWithException(final VirtualFile file) throws IOException {
    try (InputStream stream = file.getInputStream()) {
      return parseHeader(new MyXMLReader(stream));
    }
  }

  @NotNull
  public static XmlFileHeader parseHeader(final Reader reader) {
    return parseHeader(new MyXMLReader(reader));
  }

  @NotNull
  public static XmlFileHeader parseHeader(PsiFile file) {
    return parseHeader(createReader(file));
  }

  @NotNull
  private static XmlFileHeader parseHeader(final MyXMLReader r) {
    final RootTagInfoBuilder builder = new RootTagInfoBuilder();
    parse(r, builder);
    return new XmlFileHeader(builder.getRootTagName(), builder.getNamespace(), r.publicId, r.systemId);
  }

  public static String createLocation(@NonNls String ...tagNames) {
    StringBuilder result = new StringBuilder();
    for (String tagName : tagNames) {
      result.append(".");
      result.append(tagName);
    }

    return result.toString();
  }

  @Deprecated
  public static abstract class IXMLBuilderAdapter implements NanoXmlBuilder {
    // used externally
    @Deprecated
    protected static void stop() throws ParserStoppedXmlException {
      throw ParserStoppedXmlException.INSTANCE;
    }
  }

  public static class BaseXmlBuilder implements NanoXmlBuilder {
    private final Stack<String> myLocation = new Stack<>();

    @Override
    public void startBuilding(String systemID, int lineNr) {
      myLocation.push("");
    }

    @Override
    public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) throws Exception {
      myLocation.push(myLocation.peek() + "." + name);
    }

    @Override
    public void endElement(String name, String nsPrefix, String nsURI) throws Exception {
      myLocation.pop();
    }

    protected static String readText(final Reader reader) throws IOException {
      return StringFactory.createShared(StreamUtil.readTextAndConvertSeparators(reader));
    }

    protected String getLocation() {
      return myLocation.peek();
    }
  }

  public static class EmptyValidator extends NonValidator {
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
    public Reader getEntity(IXMLReader xmlReader, String name) {
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

    MyXMLReader(@NotNull Reader documentReader) {
      super(documentReader);
    }

    MyXMLReader(InputStream stream) throws IOException {
      super(stream);
    }

    @Override
    public Reader openStream(String publicId, String systemId) {
      this.publicId = StringUtil.isEmpty(publicId) ? null : publicId;
      this.systemId = StringUtil.isEmpty(systemId) ? null : systemId;

      return new StringReader(" ");
    }
  }

  public static class ParserStoppedXmlException extends XMLException {
    public static final ParserStoppedXmlException INSTANCE = new ParserStoppedXmlException();

    private ParserStoppedXmlException() {
      super("Parsing stopped");
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
      return this;
    }
  }

  private static class RootTagInfoBuilder implements IXMLBuilder {
    private String myRootTagName;
    private String myNamespace;

    @Override
    public void startBuilding(final String systemID, final int lineNr) {
    }

    @Override
    public void newProcessingInstruction(final String target, final Reader reader) {
    }

    @Override
    public void startElement(final String name, final String nsPrefix, final String nsURI, final String systemID, final int lineNr) throws Exception {
      myRootTagName = name;
      myNamespace = nsURI;
      throw ParserStoppedXmlException.INSTANCE;
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

    public String getNamespace() {
      return myNamespace;
    }

    public String getRootTagName() {
      return myRootTagName;
    }

    @Override
    public String getResult() {
      return myRootTagName;
    }
  }

}
