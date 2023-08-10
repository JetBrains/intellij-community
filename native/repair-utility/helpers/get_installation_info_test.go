package helpers

import (
	"repair/logger"
	"runtime"
	"testing"
)

func TestDetectInstallationByInnerPath(t *testing.T) {
	ideaPackage, err := GetRandomIdeInstallationPackage()
	ideaBinary, err := GetIdeBinaryByPackage(ideaPackage)
	logger.ExitWithExceptionOnError(err)
	type args struct {
		providedPath string
		returnBinary bool
	}
	tests := []struct {
		name           string
		args           args
		wantIdeaBinary string
		wantErr        bool
	}{
		{
			name:           "detect binary by package name",
			args:           args{ideaPackage, true},
			wantIdeaBinary: ideaBinary,
			wantErr:        false,
		}, {
			name:           "detect package by binary name",
			args:           args{ideaBinary, false},
			wantIdeaBinary: ideaPackage,
			wantErr:        false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			gotIdeaBinary, err := DetectInstallationByInnerPath(tt.args.providedPath, tt.args.returnBinary)
			if (err != nil) != tt.wantErr {
				t.Errorf("DetectInstallationByInnerPath() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if gotIdeaBinary != tt.wantIdeaBinary {
				t.Errorf("DetectInstallationByInnerPath() gotIdeaBinary = %v, want %v", gotIdeaBinary, tt.wantIdeaBinary)
			}
		})
	}
}

func TestGetIdeInstallationPathByBinary(t *testing.T) {
	gotPossible_binaries, _ := FindInstalledIdePackages()
	type args struct {
		ideaBinary string
	}
	tests := []struct {
		name            string
		args            args
		wantIdeaPackage string
	}{
		{
			name:            "",
			args:            args{gotPossible_binaries[0] + IdeBinaryRelatedToInstallationPath[runtime.GOOS]},
			wantIdeaPackage: gotPossible_binaries[0],
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if gotIdeaPackage := GetIdeIdePackageByBinary(tt.args.ideaBinary); gotIdeaPackage != tt.wantIdeaPackage {
				t.Errorf("GetIdeInstallationPathByBinary() = %v, want %v", gotIdeaPackage, tt.wantIdeaPackage)
			}
		})
	}
}
