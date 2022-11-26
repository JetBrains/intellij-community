package helpers

import (
	"testing"
)

func TestRequestLatestCompatibleVersionFromMarketplace(t *testing.T) {
	for {
		CurrentIde.SetBinaryToWrokWith(GetRandomIdeInstallationBinary())
		binaryInfo, err := GetIdeInfoByBinary(GetIdeaBinaryToWrokWith())
		if err != nil {
			return
		}
		if ConvertBuildNumberToFloat(binaryInfo.BuildNumber) > 201 && ConvertBuildNumberToFloat(binaryInfo.BuildNumber) < 212 {
			break
		}
	}
	ideInfo, _ := GetIdeInfoByBinary(GetIdeaBinaryToWrokWith())
	wantPluginVersion := "0"
	if ConvertBuildNumberToFloat(ideInfo.BuildNumber) > 203 && ConvertBuildNumberToFloat(ideInfo.BuildNumber) < 204 {
		wantPluginVersion = "4.0.3"
	} else if ConvertBuildNumberToFloat(ideInfo.BuildNumber) > 211 {
		wantPluginVersion = "4.1.0"
	} else if ConvertBuildNumberToFloat(ideInfo.BuildNumber) > 202 && ConvertBuildNumberToFloat(ideInfo.BuildNumber) < 203 {
		wantPluginVersion = "3.2.3.202"
	}

	pluginToCheck := PluginInfo{
		Id:            "mobi.hsz.idea.gitignore",
		Name:          ".ignore",
		Version:       "4.0.3",
		Vendor:        "JetBrains",
		MainJarPath:   GetAbsolutePath(CurrentIde.GetIdeCustomPluginsDirectory() + "/.ignore/lib/idea-gitignore-4.0.3.jar"),
		PluginXmlId:   "mobi.hsz.idea.gitignore",
		MarketplaceId: 0,
		IsDisabled:    false,
		IdeaVersion: IdeaVersion{
			SinceBuild: "203",
			UntilBuild: "203.*",
		},
	}
	type args struct {
		ideaBinary string
		plugin     *PluginInfo
	}
	tests := []struct {
		name string
		args args
		want string
	}{
		{
			name: "detect binary by package name",
			args: args{GetIdeaBinaryToWrokWith(), &pluginToCheck},
			want: wantPluginVersion,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := RequestLatestCompatibleVersionFromMarketplace(tt.args.ideaBinary, tt.args.plugin); got != tt.want {
				t.Errorf("RequestLatestCompatibleVersionFromMarketplace() = %v, want %v", got, tt.want)
			}
		})
	}
}
