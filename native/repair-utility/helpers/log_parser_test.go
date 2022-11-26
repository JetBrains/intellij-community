package helpers

import (
	"reflect"
	"repair/logger"
	"testing"
)

func TestGetLogEntriesWithExceptions(t *testing.T) {
	type args struct {
		logfile string
	}
	tests := []struct {
		name string
		args args
		want []LogEntry
	}{
		{
			name: "check first error entry in file",
			args: args{logfile: GetAbsolutePath("./test_helpers_files/logs/error_on_action.log")},
			want: []LogEntry{
				{
					DateAndTime:    "2021-02-15 18:09:37,188",
					TimeSinceStart: "61174",
					Severity:       "ERROR",
					Class:          ".wm.impl.ToolWindowManagerImpl",
					Header:         "Already disposed: Project (name=HelloJava, containerState=DISPOSE_IN_PROGRESS, componentStore=/Users/konstantin.annikov/IdeaProjects/HelloJava)",
					Body:           "java.lang.IllegalStateException: Already disposed: Project (name=HelloJava, containerState=DISPOSE_IN_PROGRESS, componentStore=/Users/konstantin.annikov/IdeaProjects/HelloJava)\n\tat com.intellij.serviceContainer.ComponentManagerImpl.getMessageBus(ComponentManagerImpl.kt:139)\n\tat com.intellij.ide.navigationToolbar.NavBarListener.subscribeTo(NavBarListener.java:71)\n\tat com.intellij.ide.navigationToolbar.NavBarPanel.addNotify(NavBarPanel.java:777)\n\tat java.desktop/java.awt.Container.addNotify(Container.java:2800)\n\tat java.desktop/javax.swing.JComponent.addNotify(JComponent.java:4791)\n\tat java.desktop/java.awt.Container.addNotify(Container.java:2800)\n\tat java.desktop/javax.swing.JComponent.addNotify(JComponent.java:4791)\n\tat java.desktop/java.awt.Container.addNotify(Container.java:2800)\n\tat java.desktop/javax.swing.JComponent.addNotify(JComponent.java:4791)\n\tat java.desktop/java.awt.Container.addNotify(Container.java:2800)\n\tat java.desktop/javax.swing.JComponent.addNotify(JComponent.java:4791)\n\tat java.desktop/java.awt.Container.addNotify(Container.java:2800)\n\tat java.desktop/javax.swing.JComponent.addNotify(JComponent.java:4791)\n\tat java.desktop/java.awt.Container.addNotify(Container.java:2800)\n\tat java.desktop/javax.swing.JComponent.addNotify(JComponent.java:4791)\n\tat java.desktop/java.awt.Container.addNotify(Container.java:2800)\n\tat java.desktop/javax.swing.JComponent.addNotify(JComponent.java:4791)\n\tat java.desktop/java.awt.Container.addNotify(Container.java:2800)\n\tat java.desktop/javax.swing.JComponent.addNotify(JComponent.java:4791)\n\tat com.intellij.openapi.wm.impl.InternalDecorator.addNotify(InternalDecorator.java:292)\n\tat java.desktop/java.awt.Container.addImpl(Container.java:1146)\n\tat java.desktop/java.awt.Container.add(Container.java:436)\n\tat com.intellij.openapi.ui.ThreeComponentsSplitter.doAddComponent(ThreeComponentsSplitter.java:541)\n\tat com.intellij.openapi.ui.ThreeComponentsSplitter.setLastComponent(ThreeComponentsSplitter.java:506)\n\tat com.intellij.openapi.wm.impl.ToolWindowsPane.setComponent(ToolWindowsPane.java:300)\n\tat com.intellij.openapi.wm.impl.ToolWindowsPane.removeDecorator(ToolWindowsPane.java:233)\n\tat com.intellij.openapi.wm.impl.ToolWindowManagerImpl.removeDecoratorWithoutUpdatingState(ToolWindowManagerImpl.kt:1067)\n\tat com.intellij.openapi.wm.impl.ToolWindowManagerImpl.projectClosed(ToolWindowManagerImpl.kt:510)\n\tat com.intellij.openapi.wm.impl.ToolWindowManagerImpl$ToolWindowManagerAppLevelHelper$2.projectClosed(ToolWindowManagerImpl.kt:216)\n\tat jdk.internal.reflect.GeneratedMethodAccessor141.invoke(Unknown Source)\n\tat java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\n\tat java.base/java.lang.reflect.Method.invoke(Method.java:566)\n\tat com.intellij.util.messages.impl.MessageBusImpl.invokeListener(MessageBusImpl.java:541)\n\tat com.intellij.util.messages.impl.MessageBusConnectionImpl.deliverMessage(MessageBusConnectionImpl.java:143)\n\tat com.intellij.util.messages.impl.MessageBusImpl.doPumpMessages(MessageBusImpl.java:465)\n\tat com.intellij.util.messages.impl.MessageBusImpl.pumpWaitingBuses(MessageBusImpl.java:426)\n\tat com.intellij.util.messages.impl.MessageBusImpl.pumpMessages(MessageBusImpl.java:415)\n\tat com.intellij.util.messages.impl.MessageBusImpl.sendMessage(MessageBusImpl.java:397)\n\tat com.intellij.util.messages.impl.MessageBusImpl.lambda$createTopicHandler$3(MessageBusImpl.java:237)\n\tat com.sun.proxy.$Proxy78.projectClosed(Unknown Source)\n\tat com.intellij.openapi.project.impl.ProjectManagerImpl.fireProjectClosed(ProjectManagerImpl.java:807)\n\tat com.intellij.openapi.project.impl.ProjectManagerImpl.lambda$closeProject$11(ProjectManagerImpl.java:694)\n\tat com.intellij.openapi.application.impl.ApplicationImpl.runWriteAction(ApplicationImpl.java:976)\n\tat com.intellij.openapi.project.impl.ProjectManagerImpl.closeProject(ProjectManagerImpl.java:687)\n\tat com.intellij.openapi.project.impl.ProjectManagerImpl.closeAndDisposeAllProjects(ProjectManagerImpl.java:611)\n\tat com.intellij.openapi.application.impl.ApplicationImpl.disposeSelf(ApplicationImpl.java:190)\n\tat com.intellij.openapi.application.impl.ApplicationImpl.doExit(ApplicationImpl.java:621)\n\tat com.intellij.openapi.application.impl.ApplicationImpl.exit(ApplicationImpl.java:589)\n\tat com.intellij.openapi.application.impl.ApplicationImpl.exit(ApplicationImpl.java:569)\n\tat com.intellij.ide.plugins.newui.MyPluginModel.runRestartButton(MyPluginModel.java:861)\n\tat com.intellij.ide.plugins.newui.RestartButton.lambda$new$0(RestartButton.java:13)\n\tat java.desktop/javax.swing.AbstractButton.fireActionPerformed(AbstractButton.java:1967)\n\tat java.desktop/javax.swing.AbstractButton$Handler.actionPerformed(AbstractButton.java:2308)\n\tat java.desktop/javax.swing.DefaultButtonModel.fireActionPerformed(DefaultButtonModel.java:405)\n\tat java.desktop/javax.swing.DefaultButtonModel.setPressed(DefaultButtonModel.java:262)\n\tat java.desktop/javax.swing.plaf.basic.BasicButtonListener.mouseReleased(BasicButtonListener.java:270)\n\tat java.desktop/java.awt.Component.processMouseEvent(Component.java:6650)\n\tat java.desktop/javax.swing.JComponent.processMouseEvent(JComponent.java:3345)\n\tat java.desktop/java.awt.Component.processEvent(Component.java:6415)\n\tat java.desktop/java.awt.Container.processEvent(Container.java:2263)\n\tat java.desktop/java.awt.Component.dispatchEventImpl(Component.java:5025)\n\tat java.desktop/java.awt.Container.dispatchEventImpl(Container.java:2321)\n\tat java.desktop/java.awt.Component.dispatchEvent(Component.java:4857)\n\tat java.desktop/java.awt.LightweightDispatcher.retargetMouseEvent(Container.java:4918)\n\tat java.desktop/java.awt.LightweightDispatcher.processMouseEvent(Container.java:4547)\n\tat java.desktop/java.awt.LightweightDispatcher.dispatchEvent(Container.java:4488)\n\tat java.desktop/java.awt.Container.dispatchEventImpl(Container.java:2307)\n\tat java.desktop/java.awt.Window.dispatchEventImpl(Window.java:2773)\n\tat java.desktop/java.awt.Component.dispatchEvent(Component.java:4857)\n\tat java.desktop/java.awt.EventQueue.dispatchEventImpl(EventQueue.java:778)\n\tat java.desktop/java.awt.EventQueue$4.run(EventQueue.java:727)\n\tat java.desktop/java.awt.EventQueue$4.run(EventQueue.java:721)\n\tat java.base/java.security.AccessController.doPrivileged(Native Method)\n\tat java.base/java.security.ProtectionDomain$JavaSecurityAccessImpl.doIntersectionPrivilege(ProtectionDomain.java:85)\n\tat java.base/java.security.ProtectionDomain$JavaSecurityAccessImpl.doIntersectionPrivilege(ProtectionDomain.java:95)\n\tat java.desktop/java.awt.EventQueue$5.run(EventQueue.java:751)\n\tat java.desktop/java.awt.EventQueue$5.run(EventQueue.java:749)\n\tat java.base/java.security.AccessController.doPrivileged(Native Method)\n\tat java.base/java.security.ProtectionDomain$JavaSecurityAccessImpl.doIntersectionPrivilege(ProtectionDomain.java:85)\n\tat java.desktop/java.awt.EventQueue.dispatchEvent(EventQueue.java:748)\n\tat com.intellij.ide.IdeEventQueue.defaultDispatchEvent(IdeEventQueue.java:974)\n\tat com.intellij.ide.IdeEventQueue.dispatchMouseEvent(IdeEventQueue.java:912)\n\tat com.intellij.ide.IdeEventQueue._dispatchEvent(IdeEventQueue.java:844)\n\tat com.intellij.ide.IdeEventQueue.lambda$null$8(IdeEventQueue.java:449)\n\tat com.intellij.openapi.progress.impl.CoreProgressManager.computePrioritized(CoreProgressManager.java:741)\n\tat com.intellij.ide.IdeEventQueue.lambda$dispatchEvent$9(IdeEventQueue.java:448)\n\tat com.intellij.openapi.application.impl.ApplicationImpl.runIntendedWriteActionOnCurrentThread(ApplicationImpl.java:831)\n\tat com.intellij.ide.IdeEventQueue.dispatchEvent(IdeEventQueue.java:502)\n\tat java.desktop/java.awt.EventDispatchThread.pumpOneEventForFilters(EventDispatchThread.java:203)\n\tat java.desktop/java.awt.EventDispatchThread.pumpEventsForFilter(EventDispatchThread.java:124)\n\tat java.desktop/java.awt.EventDispatchThread.pumpEventsForFilter(EventDispatchThread.java:117)\n\tat java.desktop/java.awt.WaitDispatchSupport$2.run(WaitDispatchSupport.java:190)\n\tat java.desktop/java.awt.WaitDispatchSupport$4.run(WaitDispatchSupport.java:235)\n\tat java.desktop/java.awt.WaitDispatchSupport$4.run(WaitDispatchSupport.java:233)\n\tat java.base/java.security.AccessController.doPrivileged(Native Method)\n\tat java.desktop/java.awt.WaitDispatchSupport.enter(WaitDispatchSupport.java:233)\n\tat java.desktop/java.awt.Dialog.show(Dialog.java:1063)\n\tat com.intellij.openapi.ui.impl.DialogWrapperPeerImpl$MyDialog.show(DialogWrapperPeerImpl.java:708)\n\tat com.intellij.openapi.ui.impl.DialogWrapperPeerImpl.show(DialogWrapperPeerImpl.java:437)\n\tat com.intellij.openapi.ui.DialogWrapper.doShow(DialogWrapper.java:1685)\n\tat com.intellij.openapi.ui.DialogWrapper.show(DialogWrapper.java:1644)\n\tat com.intellij.ide.actions.ShowSettingsUtilImpl.showSettingsDialog(ShowSettingsUtilImpl.java:80)\n\tat com.intellij.ide.actions.ShowSettingsAction.perform(ShowSettingsAction.java:54)\n\tat com.intellij.ui.mac.MacOSApplicationProvider$Worker.lambda$null$1(MacOSApplicationProvider.java:78)\n\tat com.intellij.ui.mac.MacOSApplicationProvider$Worker.lambda$submit$7(MacOSApplicationProvider.java:175)\n\tat com.intellij.openapi.application.TransactionGuardImpl$2.run(TransactionGuardImpl.java:201)\n\tat com.intellij.openapi.application.impl.ApplicationImpl.runIntendedWriteActionOnCurrentThread(ApplicationImpl.java:831)\n\tat com.intellij.openapi.application.impl.ApplicationImpl.lambda$invokeLater$4(ApplicationImpl.java:310)\n\tat com.intellij.openapi.application.impl.FlushQueue.doRun(FlushQueue.java:80)\n\tat com.intellij.openapi.application.impl.FlushQueue.runNextEvent(FlushQueue.java:128)\n\tat com.intellij.openapi.application.impl.FlushQueue.flushNow(FlushQueue.java:46)\n\tat com.intellij.openapi.application.impl.FlushQueue$FlushNow.run(FlushQueue.java:184)\n\tat java.desktop/java.awt.event.InvocationEvent.dispatch(InvocationEvent.java:313)\n\tat java.desktop/java.awt.EventQueue.dispatchEventImpl(EventQueue.java:776)\n\tat java.desktop/java.awt.EventQueue$4.run(EventQueue.java:727)\n\tat java.desktop/java.awt.EventQueue$4.run(EventQueue.java:721)\n\tat java.base/java.security.AccessController.doPrivileged(Native Method)\n\tat java.base/java.security.ProtectionDomain$JavaSecurityAccessImpl.doIntersectionPrivilege(ProtectionDomain.java:85)\n\tat java.desktop/java.awt.EventQueue.dispatchEvent(EventQueue.java:746)\n\tat com.intellij.ide.IdeEventQueue.defaultDispatchEvent(IdeEventQueue.java:974)\n\tat com.intellij.ide.IdeEventQueue._dispatchEvent(IdeEventQueue.java:847)\n\tat com.intellij.ide.IdeEventQueue.lambda$null$8(IdeEventQueue.java:449)\n\tat com.intellij.openapi.progress.impl.CoreProgressManager.computePrioritized(CoreProgressManager.java:741)\n\tat com.intellij.ide.IdeEventQueue.lambda$dispatchEvent$9(IdeEventQueue.java:448)\n\tat com.intellij.openapi.application.impl.ApplicationImpl.runIntendedWriteActionOnCurrentThread(ApplicationImpl.java:831)\n\tat com.intellij.ide.IdeEventQueue.dispatchEvent(IdeEventQueue.java:496)\n\tat java.desktop/java.awt.EventDispatchThread.pumpOneEventForFilters(EventDispatchThread.java:203)\n\tat java.desktop/java.awt.EventDispatchThread.pumpEventsForFilter(EventDispatchThread.java:124)\n\tat java.desktop/java.awt.EventDispatchThread.pumpEventsForHierarchy(EventDispatchThread.java:113)\n\tat java.desktop/java.awt.EventDispatchThread.pumpEvents(EventDispatchThread.java:109)\n\tat java.desktop/java.awt.EventDispatchThread.pumpEvents(EventDispatchThread.java:101)\n\tat java.desktop/java.awt.EventDispatchThread.run(EventDispatchThread.java:90)\n",
				},
			},
		},
	}
	for _, tt := range tests {
		CurrentIde.SetBinaryToWrokWith(GetRandomIdeInstallationBinary())
		t.Run(tt.name, func(t *testing.T) {
			if got := GetLogEntriesWithExceptions(tt.args.logfile); !(got[0].Severity == tt.want[0].Severity && got[0].Header == tt.want[0].Header) {
				t.Errorf("GetLogEntriesWithExceptions() = %v, want %v", got, tt.want)
			}
		})
	}
	logger.RemoveLogFile()
}

func TestGetPluginsWithExceptionsInIdeaLog(t *testing.T) {
	type args struct {
		logfile string
	}
	tests := []struct {
		name string
		args args
		want []PluginInfo
	}{
		{
			name: "Blame Presentation Assistant plugin",
			args: args{logfile: GetAbsolutePath("./test_helpers_files/logs/blame_presentation_assistant.log")},
			want: []PluginInfo{
				{
					Id:            "org.nik.presentation-assistant",
					Name:          "Presentation Assistant",
					Version:       "1.0.9",
					Vendor:        "Nikolay Chashnikov",
					MainJarPath:   "",
					PluginXmlId:   "",
					MarketplaceId: 0,
					IsDisabled:    false,
					IdeaVersion:   IdeaVersion{},
					isBundled:     false,
				},
			},
		},
	}
	for _, tt := range tests {
		CurrentIde.SetBinaryToWrokWith(GetRandomIdeInstallationBinary())
		t.Run(tt.name, func(t *testing.T) {
			downloadAndInstallPlugins(GetIdeaBinaryToWrokWith(), tt.want)
			if got := GetPluginsWithExceptionsInIdeaLog(tt.args.logfile); len(got) > 0 && !(got[0].Id == tt.want[0].Id) {
				t.Errorf("GetPluginsWithExceptionsInIdeaLog() = %v, want %v", got, tt.want)
			}
			CurrentIde.removePlugins(tt.want)
		})
	}
	logger.RemoveLogFile()
}

func TestParseIdeaLogFile(t *testing.T) {
	type args struct {
		logfile string
	}
	tests := []struct {
		name           string
		args           args
		wantLogEntries []LogEntry
	}{
		{
			name: "INFO record check",
			args: args{logfile: GetAbsolutePath("./test_helpers_files/logs/error_on_action.log")},
			wantLogEntries: []LogEntry{
				{
					DateAndTime:    "2021-02-15 18:09:40,829",
					TimeSinceStart: "64816",
					Severity:       "INFO",
					Class:          "plication.impl.ApplicationImpl",
					Header:         "Write action Statistics:",
					Body:           "Event number:     8\nTotal time spent: 262ms\nAverage duration: 32ms\nMedian  duration: 25ms\nMax     duration: 73ms (it was 'write action (class com.intellij.openapi.module.impl.ModuleManagerImpl$$Lambda$1556/0x0000000801aec040)')\n",
				},
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if gotLogEntries := ParseIdeaLogFile(tt.args.logfile); !(gotLogEntries[21].Header == tt.wantLogEntries[0].Header && gotLogEntries[21].Severity == tt.wantLogEntries[0].Severity) && gotLogEntries[21].TimeSinceStart == tt.wantLogEntries[0].TimeSinceStart {
				t.Errorf("ParseIdeaLogFile() = %v, want %v", gotLogEntries, tt.wantLogEntries)
			}
		})
	}
}

func Test_collectPluginsWithExceptions(t *testing.T) {
	type args struct {
		currentEntry LogEntry
	}
	tests := []struct {
		name string
		args args
	}{
		// TODO: Add test cases.
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
		})
	}
}

func Test_entryContainsPluginToBlame(t *testing.T) {
	type args struct {
		entry LogEntry
	}
	tests := []struct {
		name string
		args args
		want bool
	}{
		// TODO: Add test cases.
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := entryContainsPluginToBlame(tt.args.entry); got != tt.want {
				t.Errorf("entryContainsPluginToBlame() = %v, want %v", got, tt.want)
			}
		})
	}
}

func Test_getClass(t *testing.T) {
	type args struct {
		s string
	}
	tests := []struct {
		name string
		args args
		want string
	}{
		// TODO: Add test cases.
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := getClass(tt.args.s); got != tt.want {
				t.Errorf("getClass() = %v, want %v", got, tt.want)
			}
		})
	}
}

func Test_getEnabledPluginByBlameString(t *testing.T) {
	type args struct {
		currentEntry LogEntry
	}
	tests := []struct {
		name string
		args args
		want *PluginInfo
	}{
		// TODO: Add test cases.
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := getEnabledPluginByBlameString(tt.args.currentEntry); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("getEnabledPluginByBlameString() = %v, want %v", got, tt.want)
			}
		})
	}
}

func Test_getPluginNameByBlameString(t *testing.T) {
	type args struct {
		logHeader string
	}
	tests := []struct {
		name string
		args args
		want string
	}{
		// TODO: Add test cases.
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := getPluginNameByBlameString(tt.args.logHeader); got != tt.want {
				t.Errorf("getPluginNameByBlameString() = %v, want %v", got, tt.want)
			}
		})
	}
}

func Test_getRawTimeSinceStart(t *testing.T) {
	type args struct {
		str *string
	}
	tests := []struct {
		name string
		args args
		want string
	}{
		// TODO: Add test cases.
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := getRawTimeSinceStart(tt.args.str); got != tt.want {
				t.Errorf("getRawTimeSinceStart() = %v, want %v", got, tt.want)
			}
		})
	}
}

func Test_getSeverity(t *testing.T) {
	type args struct {
		s *string
	}
	tests := []struct {
		name string
		args args
		want string
	}{
		// TODO: Add test cases.
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := getSeverity(tt.args.s); got != tt.want {
				t.Errorf("getSeverity() = %v, want %v", got, tt.want)
			}
		})
	}
}

func Test_getTimeSinceStart(t *testing.T) {
	type args struct {
		s string
	}
	tests := []struct {
		name string
		args args
		want string
	}{
		// TODO: Add test cases.
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := getTimeSinceStart(tt.args.s); got != tt.want {
				t.Errorf("getTimeSinceStart() = %v, want %v", got, tt.want)
			}
		})
	}
}

func Test_getTimeStampFromString(t *testing.T) {
	type args struct {
		str string
	}
	tests := []struct {
		name string
		args args
		want string
	}{
		// TODO: Add test cases.
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := getTimeStampFromString(tt.args.str); got != tt.want {
				t.Errorf("getTimeStampFromString() = %v, want %v", got, tt.want)
			}
		})
	}
}

func Test_isTimeStamp(t *testing.T) {
	type args struct {
		str string
	}
	tests := []struct {
		name string
		args args
		want bool
	}{
		// TODO: Add test cases.
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := isTimeStamp(tt.args.str); got != tt.want {
				t.Errorf("isTimeStamp() = %v, want %v", got, tt.want)
			}
		})
	}
}

func Test_parseLogString(t *testing.T) {
	type args struct {
		logEntryAsString string
	}
	tests := []struct {
		name             string
		args             args
		wantCurrentEntry LogEntry
	}{
		// TODO: Add test cases.
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if gotCurrentEntry := parseLogString(tt.args.logEntryAsString); !reflect.DeepEqual(gotCurrentEntry, tt.wantCurrentEntry) {
				t.Errorf("parseLogString() = %v, want %v", gotCurrentEntry, tt.wantCurrentEntry)
			}
		})
	}
}

func Test_previousLogEntryHasException(t *testing.T) {
	type args struct {
		logEntries []LogEntry
	}
	tests := []struct {
		name string
		args args
		want bool
	}{
		// TODO: Add test cases.
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := lastLogEntryHasException(tt.args.logEntries); got != tt.want {
				t.Errorf("lastLogEntryHasException() = %v, want %v", got, tt.want)
			}
		})
	}
}

func Test_trimFoundPart(t *testing.T) {
	type args struct {
		stringToCut *string
		part        string
	}
	tests := []struct {
		name string
		args args
	}{
		// TODO: Add test cases.
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
		})
	}
}
