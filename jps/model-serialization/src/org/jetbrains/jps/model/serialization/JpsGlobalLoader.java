package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.util.io.FileUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer;
import org.jetbrains.jps.model.serialization.library.JpsSdkTableSerializer;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * @author nik
 */
public class JpsGlobalLoader extends JpsLoaderBase {
  public static final String SDK_TABLE_COMPONENT_NAME = "ProjectJdkTable";
  private static final JpsElementChildRole<JpsSimpleElement<Map<String, String>>> PATH_VARIABLES_ROLE = JpsElementChildRoleBase.create("path variables");
  private static final JpsGlobalExtensionSerializer[] SERIALIZERS = {
    new GlobalLibrariesSerializer(), new SdkTableSerializer(), new FileTypesSerializer()
  };
  private final JpsGlobal myGlobal;

  public JpsGlobalLoader(JpsGlobal global, Map<String, String> pathVariables) {
    super(new JpsMacroExpander(pathVariables));
    myGlobal = global;
    global.getContainer().setChild(PATH_VARIABLES_ROLE, JpsElementFactory.getInstance().createSimpleElement(pathVariables));
  }

  public static void loadGlobalSettings(JpsGlobal global, Map<String, String> pathVariables, String optionsPath) throws IOException {
    File optionsDir = new File(FileUtil.toCanonicalPath(optionsPath));
    new JpsGlobalLoader(global, pathVariables).load(optionsDir);
  }

  @Nullable
  public static String getPathVariable(JpsGlobal global, String name) {
    JpsSimpleElement<Map<String, String>> child = global.getContainer().getChild(PATH_VARIABLES_ROLE);
    return child != null ? child.getData().get(name) : null;
  }

  private void load(File optionsDir) {
    for (JpsGlobalExtensionSerializer serializer : SERIALIZERS) {
      loadGlobalComponents(optionsDir, serializer);
    }
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsGlobalExtensionSerializer serializer : extension.getGlobalExtensionSerializers()) {
        loadGlobalComponents(optionsDir, serializer);
      }
    }
  }

  private void loadGlobalComponents(File optionsDir, JpsGlobalExtensionSerializer serializer) {
    loadComponents(optionsDir, "other.xml", serializer, myGlobal);
  }

  private static class GlobalLibrariesSerializer extends JpsGlobalExtensionSerializer {
    private GlobalLibrariesSerializer() {
      super("applicationLibraries.xml", "libraryTable");
    }

    @Override
    public void loadExtension(@NotNull JpsGlobal global, @NotNull Element componentTag) {
      JpsLibraryTableSerializer.loadLibraries(componentTag, global.getLibraryCollection());
    }

    @Override
    public void saveExtension(@NotNull JpsGlobal global, @NotNull Element componentTag) {
      JpsLibraryTableSerializer.saveLibraries(global.getLibraryCollection(), componentTag);
    }
  }

  private static class SdkTableSerializer extends JpsGlobalExtensionSerializer {
    private SdkTableSerializer() {
      super("jdk.table.xml", SDK_TABLE_COMPONENT_NAME);
    }

    @Override
    public void loadExtension(@NotNull JpsGlobal global, @NotNull Element componentTag) {
      JpsSdkTableSerializer.loadSdks(componentTag, global.getLibraryCollection());
    }

    @Override
    public void saveExtension(@NotNull JpsGlobal global, @NotNull Element componentTag) {
      JpsSdkTableSerializer.saveSdks(global.getLibraryCollection(), componentTag);
    }
  }

  private static class FileTypesSerializer extends JpsGlobalExtensionSerializer {
    private FileTypesSerializer() {
      super("filetypes.xml", "FileTypeManager");
    }

    @Override
    public void loadExtension(@NotNull JpsGlobal global, @NotNull Element componentTag) {
      Element ignoreFilesTag = componentTag.getChild("ignoreFiles");
      if (ignoreFilesTag != null) {
        global.getFileTypesConfiguration().setIgnoredPatternString(ignoreFilesTag.getAttributeValue("list"));
      }
    }

    @Override
    public void saveExtension(@NotNull JpsGlobal global, @NotNull Element componentTag) {
    }
  }
}
