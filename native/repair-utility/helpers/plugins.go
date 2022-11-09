package helpers

import (
	"strconv"
)

type IdeaVersion struct {
	SinceBuild string `xml:"since-build,attr"`
	UntilBuild string `xml:"until-build,attr"`
}

var disabledPlugins []string

func (plugins *PluginInfoList) ToString() (pluginsAsString string) {
	for i, plugin := range *plugins {
		pluginsAsString = pluginsAsString + " " + strconv.Itoa(i+1) + ". " + plugin.Name + "\n"
	}
	return
}
func (plugins *PluginInfoList) Contains(pluginToCheck PluginInfo) bool {
	for _, plugin := range *plugins {
		if pluginToCheck == plugin {
			return true
		}
	}
	return false
}
func (plugins *PluginInfoList) FilterIncompatible() (incompatiblePlugins PluginInfoList) {
	ideBuildAsFloat := ConvertBuildNumberToFloat(CurrentIde.GetInfo().BuildNumber)
	for _, plugin := range *plugins {
		pluginSinceBuildAsFloat := ConvertBuildNumberToFloat(plugin.IdeaVersion.SinceBuild)
		pluginUntilBuildAsFloat := ConvertBuildNumberToFloat(plugin.IdeaVersion.UntilBuild)
		if ((pluginSinceBuildAsFloat > ideBuildAsFloat) || (pluginUntilBuildAsFloat < ideBuildAsFloat)) && plugin.IsDisabled == false {
			incompatiblePlugins = append(incompatiblePlugins, plugin)
		}
	}
	return incompatiblePlugins
}
func (plugins *PluginInfoList) FilterDisabled() (disabledPluginsList PluginInfoList) {
	for _, plugin := range *plugins {
		if plugin.IsDisabled {
			disabledPluginsList = append(disabledPluginsList, plugin)
		}
	}
	return disabledPluginsList
}
func (plugins *PluginInfoList) FilterEnabled() (enabledPluginsList PluginInfoList) {
	for _, plugin := range *plugins {
		if !plugin.IsDisabled {
			enabledPluginsList = append(enabledPluginsList, plugin)
		}
	}
	return enabledPluginsList
}
func (plugins *PluginInfoList) FilterUpdatable() (updatablePlugins PluginInfoList) {
	for _, plugin := range *plugins {
		if CheckIfPluginHasCompatibleVersion(CurrentIde.Binary, &plugin) {
			updatablePlugins = append(updatablePlugins, plugin)
		}
	}
	return
}
func (plugins *PluginInfoList) FilterDeprecated() (deprecatedPlugins PluginInfoList) {
	for _, plugin := range *plugins {
		if !CheckIfPluginHasCompatibleVersion(CurrentIde.Binary, &plugin) {
			deprecatedPlugins = append(deprecatedPlugins, plugin)
		}
	}
	return
}
