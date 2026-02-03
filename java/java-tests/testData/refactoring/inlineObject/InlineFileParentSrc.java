import java.io.File;

class Main {
    String getParent(String path) {
        return new <caret>File(path).getParent();
    }
}
