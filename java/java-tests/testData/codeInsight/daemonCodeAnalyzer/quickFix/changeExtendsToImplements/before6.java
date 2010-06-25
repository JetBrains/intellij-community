// "Change 'implements b' to 'extends b'" "true"
import java.io.*;

class a implements <caret>b<String> {
}

class b<T> {}
