package com.intellij.util.indexing;

import com.intellij.openapi.editor.Document;
import com.intellij.util.containers.ConcurrentFactoryMap;

import java.util.Map;

/**
 * @author Dmitry Avdeev
 * @author peter
 */
public abstract class PerIndexDocumentMap<T> {

  private final Map<Document, Map<ID, T>> myVersions = new ConcurrentFactoryMap<Document, Map<ID, T>>() {
    protected Map<ID, T> create(final Document document) {
      return new ConcurrentFactoryMap<ID,T>() {
        protected T create(ID key) {
          return createDefault(document);
        }
      };
    }
  };

  public T get(Document document, ID indexId) {
    return myVersions.get(document).get(indexId);
  }

  public void put(Document document, ID indexId, T value) {
    myVersions.get(document).put(indexId, value);
  }

  public synchronized T getAndSet(Document document, ID indexId, T value) {
    T old = get(document, indexId);
    put(document, indexId, value);
    return old;
  }

  public void clear() {
    myVersions.clear();
  }

  protected abstract T createDefault(Document document);
}
