package helpers

import (
	"reflect"
	"repair/logger"
	"testing"
)

func Test_detectRuntimeInUse(t *testing.T) {
	randomIdeaBinary := GetRandomIdeInstallationBinary()
	type args struct {
		ideaBinary string
	}
	tests := []struct {
		name             string
		args             args
		wantRuntimeInUse RuntimeInfo
		wantErr          bool
	}{
		{
			name: "RuntimeDefinedInVar",
			args: args{
				ideaBinary: randomIdeaBinary,
			},
			wantRuntimeInUse: RuntimeInfo{
				BinaryPath:          GetAbsolutePath("./test_helpers_files/jbr_packages/jbr-11/Contents/Home/bin/java"),
				BinaryPathDefinedAt: "$IDEA_JDK",
				BinaryPathDefinedAs: GetAbsolutePath("./test_helpers_files/jbr_packages/jbr-11"),
				Version:             "11.0.7",
				Architecture:        "64",
				IsBundled:           false,
			},
			wantErr: false,
		}, {
			name: "RuntimeDefinedInDefaultConfDir",
			args: args{
				ideaBinary: randomIdeaBinary,
			},
			wantRuntimeInUse: RuntimeInfo{
				BinaryPath:          GetAbsolutePath("./test_helpers_files/jbr_packages/jbr-11/Contents/Home/bin/java"),
				BinaryPathDefinedAt: GetAbsolutePath(expandRuntimeDefaultLocation(getOsDependentDir(possibleRuntimeConfiguraitonFileLocations)[0])),
				BinaryPathDefinedAs: GetAbsolutePath("./test_helpers_files/jbr_packages/jbr-11"),
				Version:             "11.0.7",
				Architecture:        "64",
				IsBundled:           false,
			},
			wantErr: false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			PrepareEnvironent(tt.wantRuntimeInUse)
			gotRuntimeInUse, err := DetectRuntimeInUse(tt.args.ideaBinary)
			if (err != nil) != tt.wantErr {
				t.Errorf("detectRuntimeInUse() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if !reflect.DeepEqual(gotRuntimeInUse, tt.wantRuntimeInUse) {
				error := "\nwant BinaryPathDefinedAt: " + tt.wantRuntimeInUse.BinaryPathDefinedAt
				error = error + "\ngot BinaryPathDefinedAt: " + gotRuntimeInUse.BinaryPathDefinedAt
				error = error + "\ngot BinaryPathDefinedAs: " + tt.wantRuntimeInUse.BinaryPathDefinedAs
				error = error + "\ngot BinaryPathDefinedAs: " + gotRuntimeInUse.BinaryPathDefinedAs
				error = error + "\ngot BinaryPath: " + tt.wantRuntimeInUse.BinaryPath
				error = error + "\ngot BinaryPath: " + gotRuntimeInUse.BinaryPath
				t.Errorf("%s", error)
			}
			ClearEnvironemnt(tt.wantRuntimeInUse)
			logger.RemoveLogFile()
		})
	}
}
