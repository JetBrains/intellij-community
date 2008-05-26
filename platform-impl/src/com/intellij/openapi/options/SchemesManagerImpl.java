package com.intellij.openapi.options;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringHash;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.UniqueFileNamesProvider;
import org.jdom.Document;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class SchemesManagerImpl extends SchemesManager {
  private static final Logger LOG = Logger.getInstance("#" + SchemesManagerImpl.class.getName());

  private static final String EXT = ".xml";

  private final Map<String, String> mySchemeNameToFileName = new HashMap<String, String>();
  private final Map<String, Long> mySchemeNameToHashValue = new HashMap<String, Long>();

  public <T extends Scheme> Collection<T> loadSchemes(String fileSpec, SchemeProcessor<T> processor, final RoamingType roamingType){

    final String path = expandMacroses(fileSpec);

    final HashMap<String, T> result = new HashMap<String, T>();

    if (path == null) return result.values();

    final File baseDir = new File(path);

    baseDir.mkdirs();


    final File[] files = baseDir.listFiles();
    if (files != null) {
      for (File file : files) {
        final String name = file.getName();
        if (file.isFile() && StringUtil.endsWithIgnoreCase(name, EXT)) {
          try {
            final Document document = JDOMUtil.loadDocument(file);
            final T scheme = processor.readScheme(document, file);
            if (scheme != null) {
              final String schemeName = scheme.getName();
              result.put(schemeName, scheme);
              final String schemeKey = fileSpec + "/" + schemeName;
              saveFileName(file, schemeKey);
              mySchemeNameToHashValue.put(schemeKey,  computeHashValue(document));
            }
          }
          catch (Exception e) {
            processor.showReadErrorMessage(e, name, file.getPath());
          }
        }
      }
    }
    else {
      LOG.error("Cannot read directory: " + baseDir.getAbsolutePath());
    }

    final StreamProvider[] providers = ((ApplicationImpl)ApplicationManager.getApplication()).getStateStore().getStateStorageManager().getStreamProviders(roamingType);

    if (providers != null) {
      for (StreamProvider provider : providers) {
        String[] paths = provider.listSubFiles(fileSpec);
        for (String subpath : paths) {
          try {
            final Document subDocument = provider.loadDocument(fileSpec + "/" + subpath, roamingType);
            if (subDocument != null) {
              final File file = new File(baseDir, subpath);
              JDOMUtil.writeDocument(subDocument, file, "\n");
              final T scheme = processor.readScheme(subDocument, file);
              final String schemeName = scheme.getName();
              result.put(schemeName, scheme);
              final String schemeKey = fileSpec + "/" + schemeName;
              saveFileName(file, schemeKey);
              mySchemeNameToHashValue.put(schemeKey,  computeHashValue(subDocument));

            }
          }
          catch (Exception e) {
            LOG.info("Cannot load data from IDEAServer: " + e.getLocalizedMessage());
          }
        }
      }
    }

    return result.values();
  }

  private void saveFileName(final File file, final String schemeKey) {
    String fileName = file.getName();
    if (StringUtil.endsWithIgnoreCase(fileName, EXT)) {
      fileName = fileName.substring(0, fileName.length() - EXT.length());
    }
    mySchemeNameToFileName.put(schemeKey, fileName);
  }

  private static long computeHashValue(final Document document) throws IOException {
    return StringHash.calc(JDOMUtil.printDocument(document, "\n"));
  }

  @Nullable
  private static String expandMacroses(final String fileSpec) {
    final Application application = ApplicationManager.getApplication();
    if (!(application instanceof ApplicationImpl)) return null;
    return ((ApplicationImpl)application).getStateStore().getStateStorageManager().expandMacroses(fileSpec);
  }

  public <T extends Scheme> void saveSchemes(Collection<T> schemes, final String fileSpec, SchemeProcessor<T> processor,
                          final RoamingType roamingType) throws

                                                                                                                      WriteExternalException {
    final String path = expandMacroses(fileSpec);

    if (path == null) {
      return;
    }

    final File baseDir = new File(path);

    baseDir.mkdirs();


    final StreamProvider[] providers = ((ApplicationImpl)ApplicationManager.getApplication()).getStateStore().getStateStorageManager().getStreamProviders(roamingType);

    UniqueFileNamesProvider fileNameProvider = new UniqueFileNamesProvider();


    for (T scheme : schemes) {
      if (processor.shouldBeSaved(scheme) ) {
        String schemeKey = fileSpec + "/" + scheme.getName();
        final String fileName;
        if (mySchemeNameToFileName.containsKey(schemeKey)) {
          fileName = mySchemeNameToFileName.get(schemeKey);
          fileNameProvider.reserveFileName(fileName);
        }
        else {
          fileName = fileNameProvider.suggestName(scheme.getName());
        }

        final File file = new File(baseDir, fileName + EXT);
        try {

          final Document document = processor.writeScheme(scheme);

          long newHash = computeHashValue(document);

          Long oldHash = mySchemeNameToHashValue.get(schemeKey);

          if (oldHash == null || newHash != oldHash.longValue() || !file.isFile()) {
            JDOMUtil.writeDocument(document, file, "\n");
            mySchemeNameToHashValue.put(schemeKey, newHash);
            saveFileName(file, schemeKey);
          }

          if (providers != null) {
            for (StreamProvider provider : providers) {
              try {
                provider.saveContent(fileSpec + "/" + file.getName(), document, roamingType);
              }
              catch (IOException e) {
                LOG.info(e);
                //ignore
              }
            }
          }

        }
        catch (IOException e) {
          processor.showWriteErrorMessage(e, scheme.getName(), file.getPath());
        }


      }


    }

  }

  public <T extends Scheme> Collection<T> loadScharedSchemes(final String dirSpec, final SchemeProcessor<T> schemeProcessor) {
    final StreamProvider[] providers = ((ApplicationImpl)ApplicationManager.getApplication()).getStateStore().getStateStorageManager().getStreamProviders(RoamingType.GLOBAL);
    final HashMap<String, T> result = new HashMap<String, T>();
    if (providers != null) {
      for (StreamProvider provider : providers) {
        String[] paths = provider.listSubFiles(dirSpec);
        for (String subpath : paths) {
          try {
            final Document subDocument = provider.loadDocument(dirSpec + "/" + subpath, RoamingType.GLOBAL);
            if (subDocument != null) {
              final T scheme = schemeProcessor.readScheme(subDocument, null);
              final String schemeName = scheme.getName();
              result.put(schemeName, scheme);
            }
          }
          catch (Exception e) {
            LOG.info("Cannot load data from IDEAServer: " + e.getLocalizedMessage());
          }
        }
      }
    }

    return result.values();

  }

  public <T extends Scheme> void exportScheme(final T scheme, final String dirSpec, final SchemeProcessor<T> schemesProcessor)
      throws WriteExternalException {
    final StreamProvider[] providers = ((ApplicationImpl)ApplicationManager.getApplication()).getStateStore().getStateStorageManager().getStreamProviders(RoamingType.GLOBAL);
    if (providers != null) {
      Document document = schemesProcessor.writeScheme(scheme);
      for (StreamProvider provider : providers) {
        try {
          provider.saveContent(dirSpec + "/" + UniqueFileNamesProvider.convertName(scheme.getName()) + EXT,
                               document, RoamingType.GLOBAL);
        }
        catch (IOException e) {
          //ignore
        }
      }
    }

  }

}
