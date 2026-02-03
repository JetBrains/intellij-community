package fleet.util.modules;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleReader;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class CompositeModuleReader implements ModuleReader {
  private final ModuleReader[] myReaders;
  private final ConcurrentHashMap<ByteBuffer, ModuleReader> bbToReaders;

  public CompositeModuleReader(ModuleReader... readers) {
    if (readers.length < 2) throw new IllegalArgumentException("must have at least two readers");
    myReaders = readers;
    bbToReaders = new ConcurrentHashMap<>();
  }

  @Override
  public Optional<URI> find(String name) throws IOException {
    for (ModuleReader r : myReaders) {
      Optional<URI> found = r.find(name);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  @Override
  public Optional<InputStream> open(String name) throws IOException {
    for (ModuleReader r : myReaders) {
      Optional<InputStream> opened = r.open(name);
      if (opened.isPresent()) {
        return opened;
      }
    }
    return Optional.empty();
  }

  @Override
  public Optional<ByteBuffer> read(String name) throws IOException {
    for (ModuleReader r : myReaders) {
      Optional<ByteBuffer> byteBuffer = r.read(name);
      if (byteBuffer.isPresent()) {
        bbToReaders.put(byteBuffer.get(), r);
        return byteBuffer;
      }
    }
    return Optional.empty();
  }

  @Override
  public void release(ByteBuffer bb) {
    var r = bbToReaders.remove(bb);
    if (r != null) {
      r.release(bb);
    }
  }

  @Override
  public Stream<String> list() throws IOException {
    Set<Stream<String>> streams = new HashSet<>();
    for (ModuleReader r : myReaders) {
      streams.add(r.list());
    }
    return streams.stream().flatMap(s -> s);
  }

  @Override
  public void close() throws IOException {
    Throwable exception = null;
    for (ModuleReader r : myReaders) {
      try {
        r.close();
      }
      catch (Throwable e) {
        if (exception == null) {
          exception = e; // we will throw the first exception we got
        }
        // we still continue to try and close all readers
      }
    }
    if (exception != null) {
      if (exception instanceof IOException) {
        throw (IOException)exception;
      }
      else if (exception instanceof RuntimeException) {
        throw (RuntimeException)exception;
      }
      else {
        throw new IOException(exception);
      }
    }
  }
}
