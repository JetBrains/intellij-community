/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fest.swing.core;

import org.fest.swing.hierarchy.ComponentHierarchy;
import org.fest.swing.hierarchy.ExistingHierarchy;
import org.fest.swing.timing.Pause;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Created by jetbrains on 09/09/16.
 */
public class AdvancedRobot extends BasicRobot {

  public AdvancedRobot(){
    super((Object)null, new ExistingHierarchy());
  }

  AdvancedRobot(@Nullable Object screenLockOwner,
                @Nonnull ComponentHierarchy hierarchy) {
    super(screenLockOwner, hierarchy);
  }

  volatile boolean isIdle = false;

  @Override
  public void waitForIdle() {
    //do not wait for idle

    //if (!myKeyboardBusy && myKeyEventDispatcher.isReady()) return;
    //
    //isIdle = false;
    //
    //this.waitIfNecessary();
    //IdeEventQueue.getInstance().doWhenReady(new Runnable() {
    //  @Override
    //  public void run() {
    //    isIdle = true;
    //  }
    //});
    //
    //assert !EventQueue.isDispatchThread();
    //Pause.pause(new Condition("Waiting for idle...") {
    //  @Override
    //  public boolean test() {
    //    return isIdle;
    //  }
    //}, 120000L);
  }

  private void waitIfNecessary() {

    int delayBetweenEvents = settings().delayBetweenEvents();
    int eventPostingDelay = settings().eventPostingDelay();
    if(eventPostingDelay > delayBetweenEvents) {
      Pause.pause((long)(eventPostingDelay - delayBetweenEvents));
    }

  }




}
