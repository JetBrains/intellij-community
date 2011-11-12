package com.intellij.diagnostic;

import com.intellij.diagnostic.errordialog.Attachment;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author ksafonov
 */
public class LogMessageEx extends LogMessage {
  private final IdeaLoggingEvent myEvent;
  private final String myTitle;
  private final String myNotificationText;
  private List<Attachment> myAttachments = null;

  /**
   * @param aEvent
   * @param title            text to show in Event Log tool window entry (it comes before 'more')
   * @param notificationText text to show in the error balloon that is popped up automatically
   */
  public LogMessageEx(IdeaLoggingEvent aEvent, String title, String notificationText) {
    super(aEvent);
    myEvent = aEvent;
    myTitle = title;
    myNotificationText = notificationText;
  }

  /**
   * @return text to show in the error balloon that is popped up automatically
   */
  public String getNotificationText() {
    return myNotificationText;
  }

  /**
   * @return text to show in Event Log tool window entry (it comes before 'more')
   */
  public String getTitle() {
    return myTitle;
  }

  public void addAttachment(String path, String content) {
    addAttachment(new Attachment(path, content));
  }

  public void addAttachment(Attachment attachment) {
    if (myAttachments == null) {
      myAttachments = new SmartList<Attachment>();
    }
    myAttachments.add(attachment);
  }

  public List<Attachment> getAttachments() {
    return myAttachments != null ? myAttachments : Collections.<Attachment>emptyList();
  }

  public IdeaLoggingEvent toEvent() {
    return myEvent;
  }

  /**
   * @param userMessage      user-friendly message description (short, single line if possible)
   * @param details          technical details (exception stack trace etc.)
   * @param attachments      attachments that will be suggested to include to the report
   * @return
   */
  public static IdeaLoggingEvent createEvent(String userMessage, final String details, final Attachment... attachments) {
    return createEvent(userMessage, details, userMessage, null, Arrays.asList(attachments));
  }


  /**
   * @param userMessage      user-friendly message description (short, single line if possible)
   * @param details          technical details (exception stack trace etc.)
   * @param title            text to show in Event Log tool window entry (it comes before 'more'), use <code>null</code> to reuse <code>userMessage</code>
   * @param notificationText text to show in the error balloon that is popped up automatically. Default is <code>com.intellij.diagnostic.IdeMessagePanel#INTERNAL_ERROR_NOTICE</code>
   * @param attachments      attachments that will be suggested to include to the report
   * @return
   */
  public static IdeaLoggingEvent createEvent(final String userMessage,
                                             final String details,
                                             @Nullable final String title,
                                             @Nullable final String notificationText,
                                             final Collection<Attachment> attachments) {
    return new IdeaLoggingEvent(userMessage, new Throwable() {
      @Override
      public void printStackTrace(PrintWriter s) {
        s.print(details);
      }

      @Override
      public void printStackTrace(PrintStream s) {
        s.print(details);
      }
    }) {
      @Override
      public Object getData() {
        final LogMessageEx logMessageEx = new LogMessageEx(this, title != null ? title : userMessage, notificationText);
        for (Attachment attachment : attachments) {
          logMessageEx.addAttachment(attachment);
        }
        return logMessageEx;
      }
    };
  }

  /**
   * @param userMessage      user-friendly message description (short, single line if possible)
   * @param details          technical details (exception stack trace etc.)
   * @param title            text to show in Event Log tool window entry (it comes before 'more'), use <code>null</code> to reuse <code>userMessage</code>
   * @param notificationText text to show in the error balloon that is popped up automatically. Default is <code>com.intellij.diagnostic.IdeMessagePanel#INTERNAL_ERROR_NOTICE</code>
   * @param attachment       attachment that will be suggested to include to the report
   * @return
   */
  public static IdeaLoggingEvent createEvent(String userMessage,
                                             final String details,
                                             @Nullable final String title,
                                             @Nullable final String notificationText,
                                             @Nullable Attachment attachment) {
    return createEvent(userMessage, details, title, notificationText,
                       attachment != null ? Collections.singletonList(attachment) : Collections.<Attachment>emptyList());
  }
}
