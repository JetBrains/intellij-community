package jetbrains.antlayout.util;

import java.io.File;

/**
 * @author max
 */
public interface TempFileFactory {
    File allocateTempFile(String name);
}
