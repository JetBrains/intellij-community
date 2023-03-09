package helpers

import (
	"reflect"
	"testing"
)

func TestGetPluginsList(t *testing.T) {
	CurrentIde.SetBinaryToWrokWith(GetRandomIdeInstallationBinaryOfBuild("211"))

	tests := []struct {
		name                        string
		ideBinary                   string
		pluginsToInstall            PluginInfoList
		wantPluginsList             PluginInfoList
		wantIncompatiblePluginsList PluginInfoList
	}{
		{
			name:      "1 Enabled plugin",
			ideBinary: GetIdeaBinaryToWrokWith(),
			pluginsToInstall: []PluginInfo{
				{
					PluginXmlId: "mobi.hsz.idea.gitignore",
					Version:     "4.1.0",
				},
				{
					PluginXmlId: "BashSupport",
					Version:     "1.7.15.192",
				}},
			wantPluginsList: []PluginInfo{
				{
					Id:            "mobi.hsz.idea.gitignore",
					Name:          ".ignore",
					Version:       "4.1.0",
					Vendor:        "JetBrains",
					MainJarPath:   GetAbsolutePath(CurrentIde.GetIdeCustomPluginsDirectory() + "/.ignore/lib/idea-gitignore-4.1.0.jar"),
					PluginXmlId:   "mobi.hsz.idea.gitignore",
					MarketplaceId: 0,
					IsDisabled:    false,
					IdeaVersion: IdeaVersion{
						SinceBuild: "211",
						UntilBuild: "211.*",
					},
					isBundled: false,
				},
				{
					Id:            "",
					Name:          "BashSupport",
					Version:       "1.7.15.192",
					Vendor:        "Joachim Ansorg",
					MainJarPath:   GetAbsolutePath(CurrentIde.GetIdeCustomPluginsDirectory() + "/BashSupport/lib/bashsupport-1.7.15.192.jar"),
					PluginXmlId:   "BashSupport",
					MarketplaceId: 0,
					IsDisabled:    false,
					IdeaVersion: IdeaVersion{
						SinceBuild: "192.0",
						UntilBuild: "193.*",
					},
					isBundled: false,
				}},
			wantIncompatiblePluginsList: []PluginInfo{
				{
					Id:            "",
					Name:          "BashSupport",
					Version:       "1.7.15.192",
					Vendor:        "Joachim Ansorg",
					MainJarPath:   GetAbsolutePath(CurrentIde.GetIdeCustomPluginsDirectory() + "/BashSupport/lib/bashsupport-1.7.15.192.jar"),
					PluginXmlId:   "BashSupport",
					MarketplaceId: 0,
					IsDisabled:    false,
					IdeaVersion: IdeaVersion{
						SinceBuild: "192.0",
						UntilBuild: "193.*",
					},
					isBundled: false,
				}},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			CurrentIde.TemporarilyRemovePluginsDir(false)
			CurrentIde.TemporarilyRenameDisabledPluginsFile(false)
			CurrentIde.downloadAndInstallPlugins(tt.pluginsToInstall)
			if gotPluginsList := *CurrentIde.CustomPluginsList(); !reflect.DeepEqual(tt.wantPluginsList, gotPluginsList) {
				t.Errorf("PluginsList() = %v, want %v", gotPluginsList, tt.wantPluginsList)
			}
			if gotPluginsList := CurrentIde.CustomPluginsList().FilterIncompatible(); !reflect.DeepEqual(tt.wantIncompatiblePluginsList, gotPluginsList) {
				t.Errorf("\n IDE build %s \n FilterIncompatible() =\n %v want\n %v", CurrentIde.Info.BuildNumber, gotPluginsList.ToString(), tt.wantIncompatiblePluginsList.ToString())
			}
			CurrentIde.TemporarilyRemovePluginsDir(true)
			CurrentIde.TemporarilyRenameDisabledPluginsFile(true)
		})
	}
}
