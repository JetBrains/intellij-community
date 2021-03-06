package org.jetbrains.jps.incremental.messages;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;

import java.util.Collection;

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

  private static String composeMessageText(Collection<? extends BuildTarget<?>> targets, Event event) {
    String targetsString = StringUtil.join(targets, dom -> dom.getPresentableName(), ", ");
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
