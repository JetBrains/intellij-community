import java.io.IOException;

class Ex extends Exception {}

interface IOCloseable {
  void close() throws IOException;
}

interface ExCloseable {
  void close() throws Ex;
}

interface NoExCloseable {
  void close();
}

interface TCloseable<T extends Throwable> {
  void close() throws T;
}

interface I1 extends AutoCloseable, IOCloseable {}
interface I1R extends IOCloseable, AutoCloseable {}
interface I2 extends AutoCloseable, IOCloseable, ExCloseable {}
interface I3 extends AutoCloseable, NoExCloseable {}
interface I3R extends NoExCloseable, AutoCloseable {}

interface IT extends AutoCloseable, TCloseable<IOException> {}

class Main {
  {
    try (<error descr="Unhandled exception from auto-closeable resource: java.io.IOException">I1 i1 = null</error>) {}
    try (I1 i11 = null) {
    } catch (IOException e){}
    try (I1R i11r = null) {
    } catch (IOException e){}

    try (I2 i2 = null) {}
    try (I2 i21 = null) {
    } catch (<error descr="Exception 'java.io.IOException' is never thrown in the corresponding try block">IOException e</error>) {}
    try (I2 i22 = null) {
    } catch (<error descr="Exception 'Ex' is never thrown in the corresponding try block">Ex e</error>) {}

    try (I3 i3 = null) {}
    try (I3R i3r = null) {}

    try (<error descr="Unhandled exception from auto-closeable resource: java.io.IOException">IT it = null</error>) {}
  }
}
