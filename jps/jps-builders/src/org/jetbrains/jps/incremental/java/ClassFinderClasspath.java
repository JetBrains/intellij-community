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

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import sun.misc.Resource;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class ClassFinderClasspath {
  private static final String FILE_PROTOCOL = "file";

  private final Stack<URL> myUrls = new Stack<URL>();
  private final List<Loader> myLoaders = new ArrayList<Loader>();
  private final Map<URL,Loader> myLoadersMap = new HashMap<URL, Loader>();

  public ClassFinderClasspath(URL[] urls) {
    push(urls);
  }

  @Nullable
  public Resource getResource(String s, boolean flag) {
    int i = 0;
    for (Loader loader; (loader = getLoader(i)) != null; i++) {
      Resource resource = loader.getResource(s, flag);
      if (resource != null) {
        return resource;
      }
    }

    return null;
  }

  public void releaseResources() {
    for (Loader loader : myLoaders) {
      loader.releaseResources();
    }
    myLoaders.clear();
    myLoadersMap.clear();
    myUrls.clear();
  }

  @Nullable
  private synchronized Loader getLoader(int i) {
    while (myLoaders.size() < i + 1) {
      URL url;
      synchronized (myUrls) {
        if (myUrls.empty()) {
          return null;
        }
        url = myUrls.pop();
      }

      if (myLoadersMap.containsKey(url)) {
        continue;
      }

      Loader loader;
      try {
        loader = getLoader(url, myLoaders.size());
        if (loader == null) {
          continue;
        }
      }
      catch (IOException ioexception) {
        continue;
      }

      myLoaders.add(loader);
      myLoadersMap.put(url, loader);
    }

    return myLoaders.get(i);
  }

  @Nullable
  private Loader getLoader(final URL url, int index) throws IOException {
    String s;
    try {
      s = url.toURI().getSchemeSpecificPart();
    }
    catch (URISyntaxException thisShouldNotHappen) {
      thisShouldNotHappen.printStackTrace();
      s = url.getFile();
    }

    Loader loader = null;
    if (s != null  && new File(s).isDirectory()) {
      if (FILE_PROTOCOL.equals(url.getProtocol())) {
        loader = new FileLoader(url, index);
      }
    }
    else {
      loader = new JarLoader(url, index);
    }

    return loader;
  }

  private void push(URL[] urls) {
    if (urls.length == 0) return;
    synchronized (myUrls) {
      for (int i = urls.length - 1; i >= 0; i--) {
        myUrls.push(urls[i]);
      }
    }
  }


  private abstract static class Loader {
    protected static final String JAR_PROTOCOL = "jar";
    protected static final String FILE_PROTOCOL = "file";

    private final URL myURL;
    private final int myIndex;

    protected Loader(URL url, int index) {
      myURL = url;
      myIndex = index;
    }


    protected URL getBaseURL() {
      return myURL;
    }

    @Nullable
    public abstract Resource getResource(final String name, boolean flag);

    public abstract void releaseResources();

    public int getIndex() {
      return myIndex;
    }

  }

  private static class FileLoader extends Loader {
    private final File myRootDir;

    @SuppressWarnings({"HardCodedStringLiteral"})
    FileLoader(URL url, int index) throws IOException {
      super(url, index);
      if (!FILE_PROTOCOL.equals(url.getProtocol())) {
        throw new IllegalArgumentException("url");
      }
      else {
        final String s = FileUtil.unquote(url.getFile());
        myRootDir = new File(s);
      }
    }

    public void releaseResources() {
    }

    @Nullable
    public Resource getResource(final String name, boolean check) {
      URL url = null;
      File file = null;

      try {
        url = new URL(getBaseURL(), name);
        if (!url.getFile().startsWith(getBaseURL().getFile())) {
          return null;
        }

        file = new File(myRootDir, name.replace('/', File.separatorChar));
        if (!check || file.exists()) {     // check means we load or process resource so we check its existence via old way
          return new FileResource(name, url, file, !check);
        }
      }
      catch (Exception exception) {
        if (!check && file != null && file.exists()) {
          try {   // we can not open the file if it is directory, Resource still can be created
            return new FileResource(name, url, file, false);
          }
          catch (IOException ex) {}
        }
      }
      return null;
    }

    private class FileResource extends Resource {
      private final String myName;
      private final URL myUrl;
      private final File myFile;

      public FileResource(String name, URL url, File file, boolean willLoadBytes) throws IOException {
        myName = name;
        myUrl = url;
        myFile = file;
        if (willLoadBytes) getByteBuffer(); // check for existence by creating cached file input stream
      }

      public String getName() {
        return myName;
      }

      public URL getURL() {
        return myUrl;
      }

      public URL getCodeSourceURL() {
        return getBaseURL();
      }

      public InputStream getInputStream() throws IOException {
        return new BufferedInputStream(new FileInputStream(myFile));
      }

      public int getContentLength() throws IOException {
        return -1;
      }

      public String toString() {
        return myFile.getAbsolutePath();
      }
    }

    @NonNls
    public String toString() {
      return "FileLoader [" + myRootDir + "]";
    }
  }

  private class JarLoader extends Loader {
    private final URL myURL;
    private ZipFile myZipFile;

    JarLoader(URL url, int index) throws IOException {
      super(new URL(JAR_PROTOCOL, "", -1, url + "!/"), index);
      myURL = url;
    }

    public void releaseResources() {
      final ZipFile zipFile = myZipFile;
      if (zipFile != null) {
        myZipFile = null;
        try {
          zipFile.close();
        }
        catch (IOException e) {
          throw new RuntimeException();
        }
      }
    }

    @Nullable
    private ZipFile acquireZipFile() throws IOException {
      ZipFile zipFile = myZipFile;
      if (zipFile == null) {
        zipFile = doGetZipFile();
        myZipFile = zipFile;
      }
      return zipFile;
    }

    @Nullable
    private ZipFile doGetZipFile() throws IOException {
      if (FILE_PROTOCOL.equals(myURL.getProtocol())) {
        String s = FileUtil.unquote(myURL.getFile());
        if (!new File(s).exists()) {
          throw new FileNotFoundException(s);
        }
        else {
          return new ZipFile(s);
        }
      }

      return null;
    }

    @Nullable
    public Resource getResource(String name, boolean flag) {
      try {
        final ZipFile file = acquireZipFile();
        if (file != null) {
          final ZipEntry entry = file.getEntry(name);
          if (entry != null) {
            return new JarResource(entry, new URL(getBaseURL(), name));
          }
        }
      }
      catch (Exception e) {
        return null;
      }
      return null;
    }

    private class JarResource extends Resource {
      private final ZipEntry myEntry;
      private final URL myUrl;

      public JarResource(ZipEntry name, URL url) {
        myEntry = name;
        myUrl = url;
      }

      public String getName() {
        return myEntry.getName();
      }

      public URL getURL() {
        return myUrl;
      }

      public URL getCodeSourceURL() {
        return myURL;
      }

      @Nullable
      public InputStream getInputStream() throws IOException {
        ZipFile file = null;
        try {
          file = acquireZipFile();
          if (file == null) {
            return null;
          }

          final InputStream inputStream = file.getInputStream(myEntry);
          if (inputStream == null) {
            return null; // if entry was not found
          }
          return new FilterInputStream(inputStream) {};
        }
        catch (IOException e) {
          e.printStackTrace();
          return null;
        }
      }

      public int getContentLength() {
        return (int)myEntry.getSize();
      }
    }

    @NonNls
    public String toString() {
      return "JarLoader [" + myURL + "]";
    }
  }


}
