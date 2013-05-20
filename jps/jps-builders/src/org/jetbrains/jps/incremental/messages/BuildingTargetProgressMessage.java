package org.jetbrains.jps.incremental.messages;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;

import java.util.Collection;

/**
 * @author nik
 */
public class BuildingTargetProgressMessage extends BuildMessage {
  private final Collection<? extends BuildTarget<?>> myTargets;
  @NotNull private final Event myEventType;

  public enum Event {
    STARTED, FINISHED
  }

  public BuildingTargetProgressMessage(@NotNull Collection<? extends BuildTarget<?>> targets, @NotNull Event event) {
    super(composeMessageText(targets, event), Kind.PROGRESS);
    myTargets = targets;
    myEventType = event;
  }

  private static <T extends BuildTarget<?>> String composeMessageText(Collection<T> targets, Event event) {
    String targetsString = StringUtil.join(targets, new NotNullFunction<T, String>() {
      @NotNull
      @Override
      public String fun(T dom) {
        return dom.getPresentableName();
      }
    }, ", ");
    return (event == Event.STARTED ? "Started" : "Finished") + " building " + targetsString;
  }

  @NotNull
  public Collection<? extends BuildTarget<?>> getTargets() {
    return myTargets;
  }

  @NotNull
  public Event getEventType() {
    return myEventType;
  }
}
