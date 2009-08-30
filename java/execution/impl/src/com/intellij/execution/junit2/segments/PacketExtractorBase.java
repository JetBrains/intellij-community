package com.intellij.execution.junit2.segments;

import com.intellij.rt.execution.junit.segments.PacketProcessor;

/**
 * @author dyoma
 */
public abstract class PacketExtractorBase {
  private DeferedActionsQueue myFulfilledWorkGate = null;

  public void setFulfilledWorkGate(final DeferedActionsQueue fulfilledWorkGate) {
    myFulfilledWorkGate = fulfilledWorkGate;
  }

  public abstract void setPacketProcessor(PacketProcessor packetProcessor);

  public void setDispatchListener(final DispatchListener listener) {
    myFulfilledWorkGate.setDispactchListener(listener);
  }

  protected void perform(final Runnable runnable) {
    myFulfilledWorkGate.addLast(runnable);
  }
}
