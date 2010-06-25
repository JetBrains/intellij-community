// "Change 'implements b' to 'extends b'" "true"
import java.io.*;

class a extends b<String> <caret>{
}

class b<T> {}
