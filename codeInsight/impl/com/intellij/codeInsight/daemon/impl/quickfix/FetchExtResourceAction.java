package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.j2ee.openapi.impl.ExternalResourceManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.URIReferenceProvider;
import com.intellij.psi.impl.source.xml.XmlEntityRefImpl;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.IOExceptionDialog;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * @author mike
 */
public class FetchExtResourceAction extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.FetchDtdAction");
  private static final @NonNls String HTML_MIME = "text/html";
  private static final @NonNls String HTTP_PROTOCOL = "http://";
  private static final @NonNls String HTTPS_PROTOCOL = "https://";
  private static final @NonNls String FTP_PROTOCOL = "ftp://";
  private static final @NonNls String EXT_RESOURCES_FOLDER = "extResources";

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if (!(file instanceof XmlFile)) return false;

    int offset = editor.getCaretModel().getOffset();
    String uri = findUri(file, offset);

    if (uri == null) return false;

    XmlFile xmlFile = XmlUtil.findXmlFile(file, uri);
    if (xmlFile != null) return false;

    if (!uri.startsWith(HTTP_PROTOCOL) && !uri.startsWith(FTP_PROTOCOL) && !uri.startsWith(HTTPS_PROTOCOL)) return false;

    setText(QuickFixBundle.message("fetch.external.resource"));
    return true;
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fetch.external.resource");
  }

  public static String findUrl(PsiFile file, int offset, String uri) {
    final PsiElement currentElement = file.findElementAt(offset);
    final XmlAttribute attribute = PsiTreeUtil.getParentOfType(currentElement, XmlAttribute.class);

    if (attribute != null) {
      final XmlTag tag = PsiTreeUtil.getParentOfType(currentElement, XmlTag.class);

      if (tag != null) {
        final String prefix = tag.getPrefixByNamespace(XmlUtil.XML_SCHEMA_INSTANCE_URI);
        if (prefix != null) {
          final String attrValue = tag.getAttributeValue(XmlUtil.SCHEMA_LOCATION_ATT, XmlUtil.XML_SCHEMA_INSTANCE_URI);
          if (attrValue != null) {
            final StringTokenizer tokenizer = new StringTokenizer(attrValue);

            while(tokenizer.hasMoreElements()) {
              if (uri.equals(tokenizer.nextToken())) {
                if (!tokenizer.hasMoreElements()) return uri;
                final String url = tokenizer.nextToken();

                return url.startsWith(HTTP_PROTOCOL) ? url:uri;
              }

              if (!tokenizer.hasMoreElements()) return uri;
              tokenizer.nextToken(); // skip file location
            }
          }
        }
      }
    }
    return uri;
  }

  @Nullable
  public static String findUri(PsiFile file, int offset) {
    PsiReference currentRef = file.findReferenceAt(offset);
    if (( currentRef instanceof URIReferenceProvider.URLReference ||
          currentRef instanceof URIReferenceProvider.DependentNSReference
        ) &&
        currentRef.resolve() == null
       ) {
      return currentRef.getCanonicalText();
    }
    return null;
  }

  static class FetchingResourceIOException extends IOException {
    private String url;

    FetchingResourceIOException(Throwable cause, String url) {
      initCause(cause);
      this.url = url;
    }
  }

  static class FetchingResourceProblemRuntimeWrapper extends RuntimeException {
    FetchingResourceProblemRuntimeWrapper(FetchingResourceIOException cause) {
      super(cause);
    }
  }

  public void invoke(final Project project, Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final String uri = findUri(file, offset);
    if (uri == null) return;

    final String url = findUrl(file, offset, uri);

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        public void run() {
          while(true) {
            try {
              final ProgressWindow[] result = new ProgressWindow[1];

              ApplicationManager.getApplication().invokeAndWait(new Runnable() {
                public void run() {
                  final ProgressWindow progressWindow = new ProgressWindow(true, project);
                  progressWindow.setTitle(QuickFixBundle.message("fetching.resource.title"));
                  result[0] = progressWindow;
                }
              }, ModalityState.defaultModalityState());


              ProgressManager.getInstance().runProcess(new Runnable() {
                  public void run() {
                    try {
                      HttpConfigurable.getInstance().prepareURL(url);
                      fetchDtd(project, uri, url);
                    }
                    catch (IOException ex) {
                      FetchingResourceIOException exceptionDescribingIOProblem;

                      if (ex instanceof FetchingResourceIOException) {
                        exceptionDescribingIOProblem = (FetchingResourceIOException)ex;
                      } else {
                        exceptionDescribingIOProblem = new FetchingResourceIOException(ex, url);
                      }

                      throw new FetchingResourceProblemRuntimeWrapper(exceptionDescribingIOProblem);
                    }

                  }
                }, result[0]);
            }
            catch (FetchingResourceProblemRuntimeWrapper e) {
              String message = QuickFixBundle.message("error.fetching.title");
              FetchingResourceIOException ioproblem = (FetchingResourceIOException)e.getCause();

              if (!url.equals(ioproblem.url)) {
                message = QuickFixBundle.message("error.fetching.dependent.resource.title");
              }

              if (!IOExceptionDialog.showErrorDialog(
                (IOException)ioproblem.getCause(),
                message,
                QuickFixBundle.message("error.fetching.resource", ioproblem.url))
              ) {
                break; // cancel fetching
              }
              else {
                continue;  // try another time
              }
            }
            break; // success fetching
          }
        }
      });
    }
  }

  private void fetchDtd(final Project project, final String dtdUrl, final String url) throws IOException {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

    final String extResourcesPath = getExternalResourcesPath();
    final File extResources = new File(extResourcesPath);
    final boolean alreadyExists = extResources.exists();
    extResources.mkdirs();
    LOG.assertTrue(extResources.exists());

    final PsiManager psiManager = PsiManager.getInstance(project);
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      public void run() {
        Runnable action = new Runnable() {
          public void run() {
            VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(
              extResources.getAbsolutePath().replace(File.separatorChar, '/'));
            LOG.assertTrue(vFile != null);
            PsiDirectory directory = psiManager.findDirectory(vFile);
            directory.getFiles();
            if (!alreadyExists) LocalFileSystem.getInstance().addRootToWatch(vFile.getPath(), true);
          }
        };
        ApplicationManager.getApplication().runWriteAction(action);
      }
    }, indicator.getModalityState());

    final List<String> downloadedResources = new LinkedList<String>();
    final List<String> resourceUrls = new LinkedList<String>();
    final IOException[] nestedException = new IOException[1];

    try {
      final String resPath = fetchOneFile(indicator, url, project, extResourcesPath, null);
      if (resPath == null) return;
      resourceUrls.add(dtdUrl);
      downloadedResources.add(resPath);

      ApplicationManager.getApplication().invokeAndWait(
        new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                ExternalResourceManagerImpl.getInstance().addResource(dtdUrl, resPath);
                VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(resPath.replace(File.separatorChar, '/'));

                Set<String> linksToProcess = new HashSet<String>();
                Set<String> processedLinks = new HashSet<String>();
                Map<String,String> baseUrls = new HashMap<String, String>();
                VirtualFile contextFile = virtualFile;
                linksToProcess.addAll( extractEmbeddedFileReferences(virtualFile, null, psiManager) );

                while(!linksToProcess.isEmpty()) {
                  String s = linksToProcess.iterator().next();
                  linksToProcess.remove(s);
                  processedLinks.add(s);

                  final boolean absoluteUrl = s.startsWith(HTTP_PROTOCOL);
                  String resourceUrl;
                  if (absoluteUrl) {
                    resourceUrl = s;
                  } else {
                    String baseUrl = baseUrls.get(s);
                    if (baseUrl == null) baseUrl = url;

                    resourceUrl = baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1) + s;
                  }

                  String resourcePath;

                  String refname = s.substring(s.lastIndexOf('/') + 1);
                  if (absoluteUrl) refname = Integer.toHexString(s.hashCode()) + "_" + refname;
                  try {
                    resourcePath = fetchOneFile(indicator, resourceUrl, project, extResourcesPath, refname);
                  }
                  catch (IOException e) {
                    nestedException[0] = new FetchingResourceIOException(e, resourceUrl);
                    break;
                  }

                  virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(resourcePath.replace(File.separatorChar, '/'));
                  downloadedResources.add(resourcePath);

                  if (absoluteUrl) {
                    ExternalResourceManagerImpl.getInstance().addResource(s, resourcePath);
                    resourceUrls.add(s);
                  }

                  final List<String> newLinks = extractEmbeddedFileReferences(virtualFile, contextFile, psiManager);
                  for(String u:newLinks) {
                    baseUrls.put(u, resourceUrl);
                    if (!processedLinks.contains(u)) linksToProcess.add(u);
                  }
                }
              }
            });
          }
        },
        indicator.getModalityState()
      );
    } catch(IOException ex) {
      nestedException[0] = ex;
    }

    if (nestedException[0]!=null) {
      cleanup(resourceUrls,downloadedResources);
      throw nestedException[0];
    }
  }

  public static String getExternalResourcesPath() {
    return PathManager.getSystemPath() + File.separator + EXT_RESOURCES_FOLDER;
  }

  private void cleanup(final List<String> resourceUrls, final List<String> downloadedResources) {
    try {
      SwingUtilities.invokeAndWait( new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(
            new Runnable() {
              public void run() {
                for(String resourcesUrl:resourceUrls) {
                  ExternalResourceManagerImpl.getInstance().removeResource(resourcesUrl);
                }

                for(String downloadedResource:downloadedResources) {
                  try {
                    LocalFileSystem.getInstance().findFileByIoFile(new File(downloadedResource)).delete(this);
                  } catch(IOException ex) {}
                }
              }
            }
          );
        }
      });
    }
    catch (InterruptedException e) {
      LOG.error(e);
    }
    catch (InvocationTargetException e) {
      LOG.error(e);
    }
  }

  @Nullable
  private static String fetchOneFile(final ProgressIndicator indicator,
                              final String resourceUrl,
                              final Project project,
                              String extResourcesPath,
                              String refname) throws IOException {
    SwingUtilities.invokeLater(
      new Runnable() {
        public void run() {
          indicator.setText(QuickFixBundle.message("fetching.progress.indicator", resourceUrl));
        }
      }
    );

    FetchResult result = fetchData(project, resourceUrl, indicator);
    if (result == null) return null;

    if (!ApplicationManager.getApplication().isUnitTestMode() &&
          result.contentType != null &&
          result.contentType.indexOf(HTML_MIME) != -1 &&
          (new String(result.bytes)).indexOf("<html") != -1) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            Messages.showMessageDialog(project,
                                       QuickFixBundle.message("invalid.url.no.xml.file.at.location", resourceUrl),
                                       QuickFixBundle.message("invalid.url.title"),
                                       Messages.getErrorIcon());
          }
        }, indicator.getModalityState());
        return null;
      }

    int slashIndex = resourceUrl.lastIndexOf('/');
    String resPath = extResourcesPath + File.separatorChar;

    if (refname != null) { // resource is known under refname so need to save it
      resPath += refname;
      int refnameSlashIndex = resPath.lastIndexOf('/');
      if (refnameSlashIndex != -1) {
        new File(resPath.substring(0,refnameSlashIndex)).mkdirs();
      }
    } else {
      resPath += Integer.toHexString(resourceUrl.hashCode()) + "_" + resourceUrl.substring(slashIndex + 1);
    }

    if (resourceUrl.indexOf('.',slashIndex) == -1) {
      // remote url does not contain file with extension
      resPath += "." + StdFileTypes.XML.getDefaultExtension();
    }

    File res = new File(resPath);

    FileOutputStream out = new FileOutputStream(res);
    try {
      out.write(result.bytes);
    }
    finally {
      out.close();
    }
    return resPath;
  }

  private static List<String> extractEmbeddedFileReferences(XmlFile file, XmlFile context) {
    final List<String> result = new LinkedList<String>();
    if (context != null) {
      XmlEntityRefImpl.copyEntityCaches(file, context);
    }

    XmlUtil.processXmlElements(
      file,
      new PsiElementProcessor() {
        public boolean execute(PsiElement element) {
          if (element instanceof XmlEntityDecl) {
            String candidateName = null;

            for (PsiElement e = element.getLastChild(); e != null; e = e.getPrevSibling()) {
              if (e instanceof XmlAttributeValue && candidateName==null) {
                candidateName = e.getText().substring(1,e.getTextLength()-1);
              } else if (e instanceof XmlToken &&
                         candidateName != null &&
                         ((XmlToken)e).getTokenType() == XmlTokenType.XML_DOCTYPE_PUBLIC
                         ) {
                if (!result.contains(candidateName)) {
                  result.add(candidateName);
                }
                break;
              }
            }
          } else if (element instanceof XmlTag) {
            final XmlTag tag = (XmlTag)element;
            String schemaLocation = tag.getAttributeValue(XmlUtil.SCHEMA_LOCATION_ATT);
            
            if (schemaLocation != null) {
              final PsiReference[] references = tag.getAttribute(XmlUtil.SCHEMA_LOCATION_ATT, null).getValueElement().getReferences();
              if (references.length > 0) result.add(schemaLocation);
            }

            final String prefix = tag.getPrefixByNamespace(XmlUtil.XML_SCHEMA_INSTANCE_URI);
            if (prefix != null) {
              schemaLocation = tag.getAttributeValue("schemaLocation",XmlUtil.XML_SCHEMA_INSTANCE_URI);

              if (schemaLocation != null) {
                final StringTokenizer tokenizer = new StringTokenizer(schemaLocation);

                while(tokenizer.hasMoreTokens()) {
                  tokenizer.nextToken();
                  if (!tokenizer.hasMoreTokens()) break;
                  String location = tokenizer.nextToken();

                  if (!result.contains(location)) {
                    result.add(location);
                  }
                }
              }
            }
          }

          return true;
        }

      },
      true,
      true
    );
    return result;
  }

  public static List<String> extractEmbeddedFileReferences(VirtualFile vFile, VirtualFile contextVFile, PsiManager psiManager) {
    PsiFile file = psiManager.findFile(vFile);

    if (file instanceof XmlFile) {
      PsiFile contextFile = contextVFile != null? psiManager.findFile(contextVFile):null;
      return extractEmbeddedFileReferences((XmlFile)file, contextFile instanceof XmlFile ? (XmlFile)contextFile:null);
    }

    return Collections.emptyList();
  }

  static class FetchResult {
    byte[] bytes;
    String contentType;
  }

  @Nullable
  private static FetchResult fetchData(final Project project, final String dtdUrl, ProgressIndicator indicator) throws IOException {

    try {
      URL url = new URL(dtdUrl);
      HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();
      urlConnection.addRequestProperty("accept","text/xml,application/xml,text/html,*/*");
      int contentLength = urlConnection.getContentLength();
      int bytesRead = 0;

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      InputStream in = urlConnection.getInputStream();
      String contentType = urlConnection.getContentType();

      byte[] buffer = new byte[256];

      while (true) {
        int read = in.read(buffer);
        if (read < 0) break;

        out.write(buffer, 0, read);
        bytesRead += read;
        indicator.setFraction((double)bytesRead / (double)contentLength);
      }

      in.close();
      out.close();

      FetchResult result = new FetchResult();
      result.bytes = out.toByteArray();
      result.contentType = contentType;

      return result;
    }
    catch (MalformedURLException e) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            Messages.showMessageDialog(project,
                                       QuickFixBundle.message("invalid.uril.message", dtdUrl),
                                       QuickFixBundle.message("invalid.url.title"),
                                       Messages.getErrorIcon());
          }
        }, indicator.getModalityState());
      }
    }

    return null;
  }
}
