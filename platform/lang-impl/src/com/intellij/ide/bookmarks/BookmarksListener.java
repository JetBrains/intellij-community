/*
 * @author max
 */
package com.intellij.ide.bookmarks;

import com.intellij.util.messages.Topic;

public interface BookmarksListener {
  Topic<BookmarksListener> TOPIC = Topic.create("Bookmarks", BookmarksListener.class);

  void bookmarkAdded(Bookmark b);
  void bookmarkRemoved(Bookmark b);
}
